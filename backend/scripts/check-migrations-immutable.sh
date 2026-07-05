#!/usr/bin/env bash
# =============================================================
# 迁移不可变门禁（架构治理包 A20/D8）
#
# 规则：合入 main 的 Flyway 迁移只能【新增】，不能【修改】——已应用的迁移一旦被改，
# Flyway checksum 校验会让所有既有环境启动失败（V4-V9 曾发生此事故）。
#
# 本脚本在 CI 里对比 PR 与 base 分支：若 db/migration 下【既有】文件内容被改动，判失败；
# 只新增 V{N}__*.sql 才放行。base 分支通过 GITHUB_BASE_REF（PR）或 origin/main 定位。
# =============================================================
set -euo pipefail

MIG_DIR="src/main/resources/db/migration"
BASE_REF="${GITHUB_BASE_REF:-main}"

# 定位 base commit：PR 用 origin/<base>，本地/push 回退到 origin/main
if git rev-parse --verify "origin/${BASE_REF}" >/dev/null 2>&1; then
  BASE="origin/${BASE_REF}"
elif git rev-parse --verify "origin/main" >/dev/null 2>&1; then
  BASE="origin/main"
else
  echo "跳过迁移门禁：无法定位 base 分支（可能是首次提交）"
  exit 0
fi

# 取 base..HEAD 之间对迁移目录里【已存在于 base】的文件的修改（M）与删除（D）
CHANGED=$(git diff --name-status "${BASE}...HEAD" -- "${MIG_DIR}" \
  | awk '$1 ~ /^(M|D|R)/ {print $2}' || true)

if [ -n "${CHANGED}" ]; then
  echo "::error::迁移不可变纪律违例——以下已应用迁移被修改/删除/重命名（只允许新增 V{N}__*.sql）："
  echo "${CHANGED}"
  echo "如需修正既有迁移的效果，请新增一条补偿迁移（更高版本号），不要改历史文件。"
  exit 1
fi

echo "迁移门禁通过：本次改动只新增迁移，未触碰历史文件。"
