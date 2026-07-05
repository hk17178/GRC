/**
 * CSV 导出工具（M2 深度包 B46：台账导出）。
 *
 * 纯前端生成：带 UTF-8 BOM（Excel 直接双击打开中文不乱码），CRLF 行尾，
 * 含逗号/引号/换行的单元格按 RFC4180 转义。导出的是"当前筛选态"数据——
 * 调用方传入已过滤的行集合即可。
 */
export function exportCsv(filename, headers, rows) {
  const esc = (v) => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }
  const lines = [headers.map(esc).join(','), ...rows.map((r) => r.map(esc).join(','))]
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}
