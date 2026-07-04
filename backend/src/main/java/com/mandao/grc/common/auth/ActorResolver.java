package com.mandao.grc.common.auth;

/**
 * 操作人归因统一解析（七轮 7-4 / 评估报告 A2）。
 *
 * 评估发现大量留痕 actor=anonymous：根因是多数 Controller 只认 X-User 头，
 * 而浏览器走 JWT Cookie 登录后并不发 X-User。统一口径：
 *   1) 优先 {@link CurrentUserContext}（IsolationFilter 已从 JWT/回退头解析并置入）；
 *   2) 其次显式传入的 X-User 参数（兼容无过滤器的调用方，如部分测试）；
 *   3) 兜底 anonymous（仅剩极端场景，出现即值得排查）。
 */
public final class ActorResolver {

    private ActorResolver() {
    }

    /** 解析当前操作人：登录态优先，头参数次之，anonymous 兜底。 */
    public static String resolve(String headerUser) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (headerUser == null || headerUser.isBlank()) ? "anonymous" : headerUser;
    }
}
