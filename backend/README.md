# GRC 平台后端 · 组织隔离 + RLS 垂直切片（M0 技术验证）

本模块是开发第一步（D2-1 M0），用于**验证全项目第一红线：组织隔离**——
即使应用层"忘记"加 `org` 过滤、甚至被显式构造的跨子公司查询/写入绕过，
数据库**行级安全（RLS）**仍兜底拦截越权。设计依据：D1-3 §5.1、DM-2 R-12。

## 隔离三道防线
1. **统一上下文**：`IsolationFilter` 在请求入口建立 `visibleOrgs`（PoC 用 `X-User` 头模拟已认证主体；生产取 AD/本地认证 Principal）。
2. **统一注入**：`OrgScopeApplier` 在每个 `@Transactional` 内 `SET LOCAL app.visible_orgs=...`（事务级，连接池复用不串号；空则注入 `-1` → fail-closed 默认拒绝）。
3. **RLS 兜底**：业务表 `ENABLE ROW LEVEL SECURITY` + 策略 `org_id = ANY(app.visible_orgs)`。应用以**非 owner、`NOBYPASSRLS` 角色 `grc_app`** 连接，故策略对其强制生效；Flyway 以 owner 跑迁移不受限。

> 关键设计点：`Repository.findAll()` 不带任何 org 条件，隔离完全由 RLS 保证——这正是"隔离不依赖开发者每次记得加 where"的价值。后续统一数据访问层 / 自定义查询编排器（D1-8 §四）在此基础上演进。

## 目录
```
backend/
├─ pom.xml                       Spring Boot 3.2 / Java 17
└─ src/main/java/com/mandao/grc/
   ├─ GrcApplication.java
   ├─ common/isolation/          IsolationContext / VisibleOrgsService / OrgScopeApplier / IsolationFilter
   └─ modules/assessment/        Assessment / Repository / Service / Controller（业务切片）
   resources/db/migration/V1__isolation_slice.sql   schema + RLS + 种子
src/test/java/.../IsolationRlsTest.java             Testcontainers 隔离断言（5 红线用例）
deploy/
├─ docker-compose.yml            PG16+pgvector / Redis / MinIO
└─ db/init/00_roles.sql          首启创建 grc_app（NOBYPASSRLS）
```

## 运行
```bash
# 1) 起依赖（PG 自动创建 grc_app 角色）
docker compose -f deploy/docker-compose.yml up -d

# 2) 起后端（需 JDK 17 + Maven；Flyway 自动建表+种子）
cd backend && mvn spring-boot:run

# 3) 验证隔离（注意 X-User）
curl -H "X-User: pay_user"    localhost:8080/api/assessments   # 仅支付子公司 2 条
curl -H "X-User: cf_user"     localhost:8080/api/assessments   # 仅消费金融 2 条
curl -H "X-User: group_admin" localhost:8080/api/assessments   # 全集团 4 条
curl                          localhost:8080/api/assessments   # 默认拒绝 空

# 4) 跑隔离红线测试（Testcontainers 起真实 PG）
cd backend && mvn test -Dtest=IsolationRlsTest
```

## 已验证结论（真实 PostgreSQL 16，以 grc_app 非 owner 角色）
| 场景 | 期望 | 实测 |
|---|---|---|
| pay_user（visibleOrgs={12}） | 仅支付 2 条 | ✅ 101,102 |
| cf_user（visibleOrgs={13}） | 仅消金 2 条 | ✅ 201,202 |
| group_admin（{1,12,13}） | 全集团 4 条 | ✅ 4 |
| 无上下文 | 默认拒绝 0 条 | ✅ 0 |
| 跨子公司直查 `WHERE org_id=13`（scoped=12） | RLS 拦截 0 行 | ✅ 0 |
| 跨子公司 UPDATE/DELETE（scoped=12） | RLS 拦截 0 行受影响 | ✅ 0/0 |

> 对应测试：`IsolationRlsTest`；对应用例：D3-2 TC-M8-002 / TC-SEC-102；缓解风险：DM-2 R-12。

## 下一步（按 D2-1 M0 → 一期）
- 接入真实认证（AD/本地，CR-003 多域控 `(domain_id,username)`）替换 `X-User`。
- `OrgScopeApplier` 上移为 AOP 切面统一拦截 `@Transactional`，覆盖全模块。
- 扩展 RLS 至全部 `org_id` 业务表（D1-3 §5.1·D7 清单）。
- E1b 横向内核：调度/到期扫描内核 + 防篡改哈希链（D2-1 一期红线）。
