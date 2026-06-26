package com.mandao.grc.modules.assessment.form;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * docx → PDF 转换服务（表单引擎 P3）：调 LibreOffice headless（soffice）把回填后的报告 docx 转 PDF，
 * 保真官方模板格式，直接交审计。
 *
 * 部署依赖：运行环境需安装 LibreOffice（Rocky9.4：dnf install libreoffice-writer + 中文字体 google-noto-cjk）。
 * soffice 路径用 grc.report.soffice 配置（默认 "soffice"，取 PATH）。未安装/转换失败时抛出明确异常。
 *
 * 并发：每次转换用独立的临时 UserInstallation 配置目录，规避 soffice 单实例锁，允许并行。
 */
@Service
public class ReportPdfService {

    private final String sofficePath;
    private final long timeoutSeconds;

    public ReportPdfService(@Value("${grc.report.soffice:soffice}") String sofficePath,
                            @Value("${grc.report.pdf-timeout-seconds:60}") long timeoutSeconds) {
        this.sofficePath = sofficePath;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 把 docx 字节转为 PDF 字节。
     *
     * @param docxBytes 回填后的报告 docx
     * @return PDF 字节
     */
    public byte[] toPdf(byte[] docxBytes) {
        Path work = null;
        try {
            work = Files.createTempDirectory("grc-report-");
            Path in = work.resolve("report.docx");
            Files.write(in, docxBytes);
            Path profile = work.resolve("lo-profile");

            List<String> cmd = new ArrayList<>(List.of(
                    sofficePath, "--headless", "--norestore", "--nolockcheck",
                    "-env:UserInstallation=file://" + profile.toAbsolutePath(),
                    "--convert-to", "pdf", "--outdir", work.toAbsolutePath().toString(),
                    in.toAbsolutePath().toString()));

            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            // 读输出避免阻塞 + 便于排障
            String out = new String(proc.getInputStream().readAllBytes());
            boolean done = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                proc.destroyForcibly();
                throw new IllegalStateException("PDF 转换超时（>" + timeoutSeconds + "s）");
            }
            if (proc.exitValue() != 0) {
                throw new IllegalStateException("PDF 转换失败（soffice 退出码 " + proc.exitValue() + "）：" + out);
            }
            Path pdf = work.resolve("report.pdf");
            if (!Files.exists(pdf)) {
                throw new IllegalStateException("PDF 未生成，请确认已安装 LibreOffice（soffice）。输出：" + out);
            }
            return Files.readAllBytes(pdf);
        } catch (IllegalStateException e) {
            throw e;
        } catch (java.io.IOException e) {
            // soffice 不存在等
            throw new IllegalStateException("PDF 导出需部署 LibreOffice（soffice）。当前路径=" + sofficePath
                    + "，错误：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PDF 转换被中断", e);
        } finally {
            cleanup(work);
        }
    }

    /** 递归清理临时目录。 */
    private void cleanup(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception ignore) {
            // 清理失败不影响主流程
        }
    }
}
