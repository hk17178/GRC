-- =============================================================
-- V24 · AI 知识库 / 向量检索（Phase C：CR-004 AI 接入 · RAG 地基）
-- =============================================================
-- 复用 pgvector（PG16 已装 vector 扩展）做向量库，与业务同库（单库，运维最简）。
--   kb_document：被索引的知识源文档（制度/法规/义务/手工录入），按 org 分租户。
--   kb_chunk   ：文档切块 + 向量嵌入；检索增强问答(RAG)按余弦相似度召回 top-k。
-- 嵌入维度统一 1024（兼容 voyage-3 等；本地确定性兜底嵌入亦产 1024 维）。
--   若日后换用不同维度模型，需迁移变更列定义并重建索引。
-- 隔离口径与业务表一致：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- 嵌入向量经原生 SQL 读写（Hibernate 无原生 vector 类型，仿 operation_log 哈希链做法）。
-- =============================================================

-- pgvector 扩展（迁移以 postgres/owner 执行，具备建扩展权限；幂等）
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- 知识源文档 ----------
CREATE TABLE kb_document (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                         -- 隔离锚点（租户）
  title        VARCHAR(256) NOT NULL,                         -- 文档标题
  source_type  VARCHAR(32)  NOT NULL,                         -- POLICY/REGULATION/OBLIGATION/MANUAL
  source_ref   VARCHAR(128),                                  -- 来源引用（如制度编号/法规号；可空）
  content      TEXT,                                          -- 原文（切块前留存）
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',       -- PENDING/INDEXED（是否已切块嵌入）
  chunk_count  INT          NOT NULL DEFAULT 0,               -- 已生成切块数（冗余便于展示）
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE kb_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_kb_document_iso ON kb_document
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON kb_document TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE kb_document_id_seq TO grc_app;

-- ---------- 文档切块 + 向量嵌入 ----------
CREATE TABLE kb_chunk (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                         -- 隔离锚点（与所属文档同 org）
  document_id  BIGINT       NOT NULL,                         -- 所属文档
  seq          INT          NOT NULL,                         -- 块序号（文档内从 1 起）
  content      TEXT         NOT NULL,                         -- 块文本
  embedding    vector(1024),                                  -- 向量嵌入（1024 维；嵌入完成后回填）
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE kb_chunk ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_kb_chunk_iso ON kb_chunk
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON kb_chunk TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE kb_chunk_id_seq TO grc_app;

-- 文档内块序号唯一；按文档检索块用
CREATE UNIQUE INDEX uk_kb_chunk_doc_seq ON kb_chunk (document_id, seq);

-- 向量近邻索引（HNSW + 余弦距离）：相似度召回提速。空表建索引即可，后续写入自动维护。
CREATE INDEX idx_kb_chunk_embedding ON kb_chunk USING hnsw (embedding vector_cosine_ops);
