<!-- =============================================================
     组织与资产页（OrgAssetView · M6）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-org 区域 1:1 复原，
     逐区块还原其 DOM 结构与内联 CSS：
       页头 phead（M6 标识 + 标题「组织架构与资产台账」+ AD 同步 ghost 按钮
         + ＋ 登记资产 主按钮）；
       Tab 切换 tabbar（组织架构 / 资产台账 / 个人信息处理活动(ROPA)，共 3 个）；
       Tab1 组织架构：左右栅格 g-main-side
         · 左：组织树 card（tree / tn / dot2 / cnt，含 lv2、lv3 缩进层级）
         · 右：AD 同步态势 sidecard（srow 列表 + 正常状态 st.ok）
       Tab2 资产台账：
         · KPI 四卡 kpibar.k4（资产总数 / 信息系统 / 高重要性 / 评估覆盖率）
         · 左右栅格 g-16-1
           · 左：资产台账表（含数据/合规属性列：数据分级 / 个人信息 / 跨境
                 / 等保定级 / 持卡人数据 / 重要性，横向滚动 min-width:760px）
           · 右：资产类型分布 bars（seg2.a 强调色）
       Tab3 个人信息处理活动(ROPA)：ROPA 表（PIPL 法定台账，
             横向滚动 min-width:860px）
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     主题表头底色规则用更具体的 .view-orgasset 限定，避免污染其它页。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view view-orgasset">
      <!-- ===== 页头：M6 标识 + 标题 + AD 同步 + 登记资产 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('orgasset.tag') }}</div>
          <h1>{{ $t('orgasset.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button v-if="activeTab === 'org'" class="btn" :disabled="!canWrite('org')"
                :title="canWrite('org') ? '' : $t('common.noPerm')" @click="openOrgAdd(null)">＋ 新增子组织</button>
        <button v-else-if="activeTab === 'asset'" class="btn" :disabled="!canWrite('org')"
                :title="canWrite('org') ? '' : $t('common.noPerm')" @click="openAsset">＋ 登记资产</button>
        <button v-else class="btn" :disabled="!canWrite('org')"
                :title="canWrite('org') ? '' : $t('common.noPerm')" @click="openRopa">＋ 登记处理活动</button>
      </div>

      <!-- ===== Tab 切换 ===== -->
      <div class="tabbar">
        <button
          v-for="t in tabs"
          :key="t"
          :class="{ on: t === activeTab }"
          @click="activeTab = t"
        >
          {{ $t('orgasset.tab.' + t) }}
        </button>
      </div>

      <!-- ========== Tab1 · 组织架构（手动配置组织树，真实后端 /api/orgs）========== -->
      <div v-show="activeTab === 'org'" class="tabpane">
        <div class="g g-main-side">
          <!-- 左：组织树（手动维护）-->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('orgasset.tree.title') }}</h3>
              <span class="more">手动配置（新增 / 重命名 / 删除）</span>
            </div>
            <div class="cb">
              <div class="otree">
                <div v-for="n in orgs" :key="n.id" class="orow" :style="{ paddingLeft: (depth(n) * 18) + 'px' }">
                  <span class="dot2"></span>
                  <span class="onm">{{ n.name }}</span>
                  <span class="ocode">{{ n.code }}</span>
                  <span class="opill">{{ orgTypeLabel(n.orgType) }}</span>
                  <span class="ospc"></span>
                  <template v-if="canWrite('org')">
                    <button class="mini" title="新增子组织" @click="openOrgAdd(n)">＋</button>
                    <button class="mini" title="重命名" @click="openRename(n)">✎</button>
                    <button class="mini danger" title="删除" @click="delOrg(n)">🗑</button>
                  </template>
                </div>
                <div v-if="!orgs.length" class="hint">{{ orgError || '加载组织树…' }}</div>
              </div>
              <div v-if="orgMsg" class="ok-msg">{{ orgMsg }}</div>
            </div>
          </div>

          <!-- 右：手动配置说明侧卡（替代 AD 同步） -->
          <div class="sidecard">
            <h4>组织树 · 手动配置</h4>
            <p class="sidenote">组织架构由管理员手动维护，不依赖 AD 同步。可在任意节点新增子组织、重命名，或删除无子组织的叶子节点。</p>
            <div class="srow"><span>组织节点数</span><b>{{ orgs.length }}</b></div>
            <div class="srow"><span>层级</span><b>{{ maxDepth }} 级</b></div>
            <div class="srow"><span>维护方式</span><span class="st ok"><span class="d"></span>手动</span></div>
            <p class="sidenote" style="margin-top:10px;color:var(--text-3)">删除组织不会级联清理其下业务数据，请先确认无在用资产/记录。</p>
          </div>
        </div>
      </div>

      <!-- 登记资产弹窗（真实 POST /api/assets，含合规属性 CR-002）-->
      <div v-if="showAsset" class="modal-mask" @click.self="showAsset = false">
        <div class="modal-card">
          <h3>{{ editingAssetId ? '编辑资产' : '登记资产' }}</h3>
          <label class="fld">资产名称<input v-model="af.name" placeholder="如 核心支付网关" /></label>
          <label class="fld">类型
            <select v-model="af.assetType"><option value="SYSTEM">系统</option><option value="DATABASE">数据库</option><option value="APP">应用</option><option value="PROCESS">流程</option><option value="VENDOR_SVC">供应商服务</option></select>
          </label>
          <label class="fld">责任人<input v-model="af.owner" placeholder="如 张三" /></label>
          <label class="fld">数据分级
            <select v-model="af.classification"><option value="PUBLIC">公开</option><option value="INTERNAL">内部</option><option value="SENSITIVE">敏感</option></select>
          </label>
          <label class="fld">重要性
            <select v-model="af.criticality"><option value="HIGH">高</option><option value="MID">中</option><option value="LOW">低</option></select>
          </label>
          <div class="chkrow">
            <label class="chk"><input type="checkbox" v-model="af.containsPi" /> 含个人信息</label>
            <label class="chk"><input type="checkbox" v-model="af.crossBorder" /> 跨境</label>
            <label class="chk"><input type="checkbox" v-model="af.mlpsFiled" /> 等保已备案</label>
            <label class="chk"><input type="checkbox" v-model="af.containsChd" /> 含持卡人数据</label>
          </div>
          <!-- M2 深度包 B47：合规属性深化 -->
          <label class="fld">等保定级
            <select v-model="af.mlpsLevel"><option :value="null">未定级</option><option :value="1">一级</option><option :value="2">二级</option><option :value="3">三级</option><option :value="4">四级</option></select>
          </label>
          <label class="fld">等保测评到期日<input v-model="af.mlpsReviewDue" type="date" /></label>
          <label class="fld">CIA 三性评级<input v-model="af.ciaRating" placeholder="如 3-3-2（机密-完整-可用 各1~3）" /></label>
          <label class="fld">网络区域
            <select v-model="af.networkZone"><option :value="null">—</option><option value="生产核心区">生产核心区</option><option value="DMZ">DMZ</option><option value="办公网">办公网</option><option value="托管机房">托管机房</option><option value="云上VPC">云上VPC</option></select>
          </label>
          <label class="fld">所属组织<select v-model.number="af.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <!-- B12 Phase1：自定义字段动态渲染（按 custom_field_def 启用项，值落 ext）-->
          <template v-if="customFields.length">
            <div class="cf-divider">自定义字段</div>
            <label v-for="f in customFields" :key="f.id" class="fld">{{ f.label }}<i v-if="f.required" style="color:var(--danger)">*</i>
              <select v-if="f.dataType === 'SELECT'" v-model="af.ext[f.fieldKey]"><option value="">—</option><option v-for="o in (f.options || '').split(';').filter(Boolean)" :key="o" :value="o.trim()">{{ o.trim() }}</option></select>
              <select v-else-if="f.dataType === 'BOOL'" v-model="af.ext[f.fieldKey]"><option value="">—</option><option value="true">是</option><option value="false">否</option></select>
              <input v-else-if="f.dataType === 'DATE'" v-model="af.ext[f.fieldKey]" type="date" />
              <input v-else-if="f.dataType === 'NUMBER'" v-model="af.ext[f.fieldKey]" type="number" />
              <input v-else v-model="af.ext[f.fieldKey]" :placeholder="f.label" />
            </label>
          </template>
          <p v-if="assetErr" class="cerr">{{ assetErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" style="margin-right:auto" @click="openCfConfig" title="定义资产的自定义字段">⚙ 字段配置</button>
            <button class="btn ghost" @click="showAsset = false">取消</button>
            <button class="btn" :disabled="!af.name || assetSaving" @click="submitAsset">{{ assetSaving ? '提交中…' : '确认登记' }}</button>
          </div>
        </div>
      </div>

      <!-- B12 Phase1：自定义字段配置弹窗（资产对象）-->
      <div v-if="showCfConfig" class="modal-mask" @click.self="showCfConfig = false">
        <div class="modal-card" style="width:560px">
          <h3>自定义字段配置 · 资产</h3>
          <p style="font-size:11.5px;color:var(--text-3);margin:-6px 0 12px">不改代码即可为资产登记扩展字段；字段值随资产按组织隔离存储，受类型/必填校验。</p>
          <table style="width:100%;font-size:12px;margin-bottom:12px">
            <thead><tr><th style="text-align:left">键</th><th style="text-align:left">标签</th><th>类型</th><th>必填</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="d in cfDefs" :key="d.id">
                <td><code>{{ d.fieldKey }}</code></td><td>{{ d.label }}</td>
                <td style="text-align:center">{{ CF_TYPE[d.dataType] || d.dataType }}</td>
                <td style="text-align:center">{{ d.required ? '是' : '—' }}</td>
                <td style="text-align:center"><span :class="d.status==='ACTIVE'?'pill blue':'pill'">{{ d.status==='ACTIVE'?'启用':'停用' }}</span></td>
                <td style="text-align:right"><button v-if="d.status==='ACTIVE'" class="mini danger" @click="retireCf(d)">停用</button></td>
              </tr>
              <tr v-if="!cfDefs.length"><td colspan="6" style="text-align:center;color:var(--text-3);padding:10px">暂无自定义字段</td></tr>
            </tbody>
          </table>
          <div class="cf-add">
            <input v-model="cf.fieldKey" placeholder="字段键(英文)" style="width:110px" />
            <input v-model="cf.label" placeholder="标签" style="width:110px" />
            <select v-model="cf.dataType"><option value="TEXT">文本</option><option value="NUMBER">数值</option><option value="DATE">日期</option><option value="BOOL">布尔</option><option value="SELECT">下拉</option></select>
            <input v-if="cf.dataType==='SELECT'" v-model="cf.options" placeholder="选项;分号隔" style="width:120px" />
            <label class="chk" style="font-size:12px"><input type="checkbox" v-model="cf.required" /> 必填</label>
            <button class="btn sm" :disabled="!cf.fieldKey || !cf.label" @click="addCf">＋ 新增</button>
          </div>
          <p v-if="cfErr" class="cerr">{{ cfErr }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="showCfConfig = false">关闭</button></div>
        </div>
      </div>

      <!-- 登记 ROPA 处理活动弹窗（真实 POST /api/ropa）-->
      <div v-if="showRopa" class="modal-mask" @click.self="showRopa = false">
        <div class="modal-card">
          <h3>登记个人信息处理活动</h3>
          <label class="fld">处理活动<input v-model="rf.activityName" placeholder="如 商户实名认证" /></label>
          <label class="fld">目的<input v-model="rf.purpose" placeholder="如 履行反洗钱义务" /></label>
          <label class="fld">数据类别<input v-model="rf.dataCategories" placeholder="如 身份证号、人脸信息" /></label>
          <!-- B14：个保法§55/56 法定字段 -->
          <label class="fld">处理方式<input v-model="rf.processingMethod" placeholder="如 收集、存储、使用、传输、对外提供" /></label>
          <label class="fld">接收方<input v-model="rf.recipients" placeholder="如 银联、征信机构、合作商户（无则留空）" /></label>
          <label class="fld">法律依据<input v-model="rf.legalBasis" placeholder="如 个保法第13条第3款" /></label>
          <label class="fld">留存期限<input v-model="rf.retention" placeholder="如 合同终止后5年" /></label>
          <label class="chk"><input type="checkbox" v-model="rf.crossBorder" /> 涉及跨境提供</label>
          <label class="fld">所属组织<select v-model.number="rf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="ropaErr" class="cerr">{{ ropaErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showRopa = false">取消</button>
            <button class="btn" :disabled="!rf.activityName || ropaSaving" @click="submitRopa">{{ ropaSaving ? '提交中…' : '确认登记' }}</button>
          </div>
        </div>
      </div>

      <!-- 组织 新增/重命名 弹窗 -->
      <div v-if="orgModal" class="modal-mask" @click.self="orgModal = null">
        <div class="modal-card">
          <h3>{{ orgModal === 'add' ? '新增子组织' : '重命名组织' }}</h3>
          <template v-if="orgModal === 'add'">
            <label class="fld">上级组织
              <select v-model.number="of.parentId">
                <option v-for="n in orgs" :key="n.id" :value="n.id">{{ '　'.repeat(depth(n)) + n.name }}</option>
              </select>
            </label>
            <label class="fld">组织编码<input v-model="of.code" placeholder="如 PAY-RISK（全局唯一）" /></label>
            <label class="fld">组织名称<input v-model="of.name" placeholder="如 支付风险部" /></label>
            <label class="fld">类型
              <select v-model="of.orgType">
                <option value="DEPT">部门 DEPT</option>
                <option value="SUBSIDIARY">子公司 SUBSIDIARY</option>
                <option value="GROUP">集团 GROUP</option>
              </select>
            </label>
          </template>
          <template v-else>
            <label class="fld">组织名称<input v-model="of.name" /></label>
          </template>
          <p v-if="orgErr" class="cerr">{{ orgErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="orgModal = null">取消</button>
            <button class="btn" :disabled="orgSaving || (orgModal === 'add' && (!of.code || !of.name)) || (orgModal === 'rename' && !of.name)" @click="submitOrg">
              {{ orgSaving ? '提交中…' : '确认' }}
            </button>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 资产台账 ========== -->
      <div v-show="activeTab === 'asset'" class="tabpane">
        <!-- KPI 四卡 -->
        <div class="kpibar k4">
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.total') }}</div>
            <div class="v">{{ assetKpi.total }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.systems') }}</div>
            <div class="v">{{ assetKpi.systems }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.highCrit') }}</div>
            <div class="v" style="color: var(--danger)">{{ assetKpi.highCrit }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.coverage') }}</div>
            <div class="v" style="color: var(--success)">{{ assetKpi.coverage }}<small>%</small></div>
          </div>
        </div>

        <div class="g g-16-1">
          <!-- 左：资产台账表（含数据/合规属性列，横向滚动）-->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('orgasset.asset.title') }}</h3>
              <span class="sub">{{ $t('orgasset.asset.sub') }}</span>
              <!-- B46：资产台账 CSV 导出（当前列表态） -->
              <button class="mini" style="margin-left:auto" :disabled="!assets.length" @click="exportAssets">导出 CSV</button>
            </div>
            <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
              <table style="min-width: 1020px">
                <thead>
                  <tr>
                    <th>{{ $t('orgasset.asset.th.id') }}</th>
                    <th>{{ $t('orgasset.asset.th.name') }}</th>
                    <th>{{ $t('orgasset.asset.th.type') }}</th>
                    <th>{{ $t('orgasset.asset.th.dataClass') }}</th>
                    <th>{{ $t('orgasset.asset.th.pi') }}</th>
                    <th>{{ $t('orgasset.asset.th.crossBorder') }}</th>
                    <th>{{ $t('orgasset.asset.th.mlps') }}</th>
                    <th>等保定级</th>
                    <th>测评到期</th>
                    <th>网络区域</th>
                    <th>{{ $t('orgasset.asset.th.chd') }}</th>
                    <th>{{ $t('orgasset.asset.th.criticality') }}</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in assets" :key="r.id">
                    <td class="code">AST-{{ r.id }}</td>
                    <td>{{ r.name }}</td>
                    <td><span class="pill">{{ ASSET_TYPE[r.assetType] || r.assetType }}</span></td>
                    <td><span class="tag" :class="CLS_TAG[r.classification]">{{ CLS_LABEL[r.classification] || r.classification }}</span></td>
                    <td>{{ r.containsPi ? '是' : '否' }}</td>
                    <td><span v-if="r.crossBorder" class="tag m">是</span><template v-else>否</template></td>
                    <td><span v-if="r.mlpsFiled" class="pill blue">已备案</span><template v-else>—</template></td>
                    <!-- B47：等保定级/测评到期/网络区域（到期 30 天内标红提醒） -->
                    <td>{{ r.mlpsLevel ? r.mlpsLevel + '级' : '—' }}</td>
                    <td :style="mlpsDueSoon(r) ? 'color:var(--danger);font-weight:600' : ''">{{ r.mlpsReviewDue || '—' }}</td>
                    <td>{{ r.networkZone || '—' }}</td>
                    <td>{{ r.containsChd ? '是' : '否' }}</td>
                    <td><span class="tag" :class="CRIT_TAG[r.criticality]">{{ CRIT_LABEL[r.criticality] || r.criticality || '—' }}</span></td>
                    <!-- 八轮 8-10（B42）：资产可编辑/退役——录错不再只能带病运行 -->
                    <td style="white-space:nowrap">
                      <template v-if="canWrite('org')">
                        <button class="mini" @click="openAssetEdit(r)">编辑</button>
                        <button class="mini danger" @click="retireAsset(r)">退役</button>
                      </template>
                    </td>
                  </tr>
                  <tr v-if="!assets.length"><td colspan="13" style="text-align:center;color:var(--text-3);padding:18px">暂无资产，点「登记资产」。</td></tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 右：资产类型分布 bars -->
          <div class="card">
            <div class="ch"><h3>{{ $t('orgasset.dist.title') }}</h3></div>
            <div class="cb">
              <div class="bars">
                <div v-for="b in distBars" :key="b.label" class="bar-row">
                  <div class="hd"><span class="nm">{{ $t(b.label) }}</span><b>{{ b.v }}</b></div>
                  <div class="track">
                    <div class="seg2 a" :style="{ width: b.w }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 个人信息处理活动(ROPA) ========== -->
      <div v-show="activeTab === 'ropa'" class="tabpane">
        <div class="card">
          <div class="ch">
            <h3>{{ $t('orgasset.ropa.title') }}</h3>
            <span class="sub">{{ $t('orgasset.ropa.sub') }}</span>
            <span class="more">{{ $t('orgasset.ropa.add') }}</span>
          </div>
          <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
            <table style="min-width: 860px">
              <thead>
                <tr>
                  <th>{{ $t('orgasset.ropa.th.activity') }}</th>
                  <th>{{ $t('orgasset.ropa.th.purpose') }}</th>
                  <th>{{ $t('orgasset.ropa.th.piType') }}</th>
                  <!-- B14：个保法法定字段列 -->
                  <th>处理方式</th>
                  <th>接收方</th>
                  <th>法律依据</th>
                  <th>{{ $t('orgasset.ropa.th.export') }}</th>
                  <th>{{ $t('orgasset.ropa.th.retention') }}</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in ropas" :key="r.id">
                  <td><b>{{ r.activityName }}</b></td>
                  <td class="mutedcell">{{ r.purpose || '—' }}</td>
                  <td>{{ r.dataCategories || '—' }}</td>
                  <td>{{ r.processingMethod || '—' }}</td>
                  <td class="mutedcell">{{ r.recipients || '—' }}</td>
                  <td class="mutedcell">{{ r.legalBasis || '—' }}</td>
                  <td><span v-if="r.crossBorder" class="tag m">是</span><template v-else>否</template></td>
                  <td>{{ r.retention || '—' }}</td>
                  <td><span class="pill" :class="r.status === 'ACTIVE' ? 'blue' : ''">{{ ROPA_ST[r.status] || r.status }}</span></td>
                </tr>
                <tr v-if="!ropas.length"><td colspan="9" style="text-align:center;color:var(--text-3);padding:18px">暂无处理活动记录，点「＋ 登记处理活动」。</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { canWrite } from '@/auth.js'
import { exportCsv } from '@/utils/csv.js'
// 六轮 #3/#4 修复：登记资产/ROPA 弹窗的「所属组织」下拉此前只 import 了 reloadOrgs，
// 模板里引用的 orgOptions/orgLabel 未定义导致下拉恒空——补齐共享组织缓存的接入
import { reloadOrgs, useOrgs, orgLabel } from '@/orgs.js'

// 各表单「所属组织」下拉的数据源（全局共享缓存，首次调用触发加载）
const orgOptions = useOrgs()

// ---- Tab 切换（顺序照搬原型 tabbar：组织架构 / 资产台账 / ROPA）----
const tabs = ['org', 'asset', 'ropa']
const activeTab = ref('org')

// ---- Tab1：组织树（手动配置，真实后端 /api/orgs/tree/1）----
const orgs = ref([])
const orgError = ref('')
const orgMsg = ref('')
async function loadTree() {
  orgError.value = ''
  try { orgs.value = await api.get('/orgs/tree/1') } catch (e) { orgError.value = e.message; orgs.value = [] }
}
// 层级深度：path '/1'→1，'/1/12'→2
const depth = (n) => ((n.path || '').match(/\//g) || []).length
const maxDepth = computed(() => orgs.value.reduce((m, n) => Math.max(m, depth(n)), 0))
const ORG_TYPE = { GROUP: '集团', SUBSIDIARY: '子公司', DEPT: '部门' }
const orgTypeLabel = (t) => ORG_TYPE[t] || t

// 新增 / 重命名
const orgModal = ref(null)   // 'add' | 'rename' | null
const orgSaving = ref(false)
const orgErr = ref('')
const of = reactive({ id: null, parentId: 1, code: '', name: '', orgType: 'DEPT' })
function openOrgAdd(parent) {
  Object.assign(of, { id: null, parentId: parent ? parent.id : (orgs.value[0]?.id || 1), code: '', name: '', orgType: 'DEPT' })
  orgErr.value = ''; orgModal.value = 'add'
}
function openRename(n) {
  Object.assign(of, { id: n.id, parentId: n.parentId, code: n.code, name: n.name, orgType: n.orgType })
  orgErr.value = ''; orgModal.value = 'rename'
}
async function submitOrg() {
  orgSaving.value = true; orgErr.value = ''
  try {
    if (orgModal.value === 'add') {
      await api.post('/orgs', { parentId: of.parentId, code: of.code, name: of.name, orgType: of.orgType })
      orgMsg.value = '已新增子组织'
    } else {
      await api.put('/orgs/' + of.id, { name: of.name })
      orgMsg.value = '已重命名'
    }
    orgModal.value = null
    await loadTree(); reloadOrgs()
    setTimeout(() => (orgMsg.value = ''), 2500)
  } catch (e) { orgErr.value = e.message } finally { orgSaving.value = false }
}
async function delOrg(n) {
  if (!window.confirm(`确认删除组织「${n.name}」？（仅限无子组织的叶子节点）`)) return
  orgMsg.value = ''; orgError.value = ''
  try {
    await api.del('/orgs/' + n.id)
    orgMsg.value = '已删除'
    await loadTree(); reloadOrgs()
    setTimeout(() => (orgMsg.value = ''), 2500)
  } catch (e) { orgError.value = e.message }
}

// ===== Tab2：资产台账（真实后端 /api/assets，含合规属性 CR-002）=====
const ASSET_TYPE = { SYSTEM: '系统', DATABASE: '数据库', APP: '应用', PROCESS: '流程', VENDOR_SVC: '供应商服务' }
const CLS_LABEL = { PUBLIC: '公开', INTERNAL: '内部', SENSITIVE: '敏感' }
const CLS_TAG = { SENSITIVE: 'h', INTERNAL: 'm', PUBLIC: 'l' }
const CRIT_LABEL = { HIGH: '高', MID: '中', LOW: '低' }
const CRIT_TAG = { HIGH: 'h', MID: 'm', LOW: 'l' }
const assets = ref([])
async function loadAssets() {
  try { assets.value = await api.get('/assets') } catch (e) { assets.value = [] }
}
// KPI（按真实资产统计：总数/系统类/高重要性/等保备案覆盖率）
const assetKpi = computed(() => {
  const total = assets.value.length
  return {
    total,
    systems: assets.value.filter((a) => a.assetType === 'SYSTEM').length,
    highCrit: assets.value.filter((a) => a.criticality === 'HIGH').length,
    coverage: total ? Math.round((assets.value.filter((a) => a.mlpsFiled).length / total) * 100) : 0
  }
})
// 右栏：资产类型分布（按真实资产统计）
const distBars = computed(() => {
  const total = assets.value.length || 1
  const byType = {}
  assets.value.forEach((a) => { byType[a.assetType] = (byType[a.assetType] || 0) + 1 })
  return Object.entries(byType).map(([k, v]) => ({ label: ASSET_TYPE[k] || k, v, w: Math.round((v / total) * 100) + '%' }))
})

// 登记资产
const showAsset = ref(false)
const assetSaving = ref(false)
const assetErr = ref('')
const af = reactive({ name: '', assetType: 'SYSTEM', owner: '', classification: 'INTERNAL', criticality: 'MID', containsPi: false, crossBorder: false, mlpsFiled: false, containsChd: false, mlpsLevel: null, mlpsReviewDue: '', ciaRating: '', networkZone: null, orgId: 12, ext: {} })
function openAsset() {
  editingAssetId.value = null // 登记模式（八轮 8-10：与编辑复用同一弹窗）
  Object.assign(af, { name: '', assetType: 'SYSTEM', owner: '', classification: 'INTERNAL', criticality: 'MID', containsPi: false, crossBorder: false, mlpsFiled: false, containsChd: false, mlpsLevel: null, mlpsReviewDue: '', ciaRating: '', networkZone: null, orgId: 12, ext: {} })
  assetErr.value = ''; showAsset.value = true
}
async function submitAsset() {
  assetSaving.value = true; assetErr.value = ''
  // B47：深化合规属性 + B12：自定义字段 ext 随表单一并提交（空值传 null）
  const ext = {}
  for (const f of customFields.value) { const v = af.ext[f.fieldKey]; if (v !== undefined && v !== '') ext[f.fieldKey] = v }
  const body = { orgId: af.orgId, name: af.name, assetType: af.assetType, owner: af.owner || null, classification: af.classification, containsPi: af.containsPi, crossBorder: af.crossBorder, mlpsFiled: af.mlpsFiled, containsChd: af.containsChd, criticality: af.criticality, mlpsLevel: af.mlpsLevel, mlpsReviewDue: af.mlpsReviewDue || null, ciaRating: af.ciaRating || null, networkZone: af.networkZone, ext }
  try {
    // 八轮 8-10（B42）：editingAssetId 非空即编辑（PUT），否则登记（POST）
    if (editingAssetId.value) await api.put('/assets/' + editingAssetId.value, body)
    else await api.post('/assets', body)
    showAsset.value = false; editingAssetId.value = null; await loadAssets()
  } catch (e) { assetErr.value = e.message } finally { assetSaving.value = false }
}

// ===== 八轮 8-10（B42）：资产编辑/退役 =====
const editingAssetId = ref(null)
function openAssetEdit(r) {
  editingAssetId.value = r.id
  Object.assign(af, { name: r.name, assetType: r.assetType, owner: r.owner || '', classification: r.classification, criticality: r.criticality || 'MID', containsPi: !!r.containsPi, crossBorder: !!r.crossBorder, mlpsFiled: !!r.mlpsFiled, containsChd: !!r.containsChd, mlpsLevel: r.mlpsLevel ?? null, mlpsReviewDue: r.mlpsReviewDue || '', ciaRating: r.ciaRating || '', networkZone: r.networkZone ?? null, orgId: r.orgId, ext: { ...(r.ext || {}) } })
  assetErr.value = ''; showAsset.value = true
}

// ===== B12 Phase1：自定义字段（资产对象）=====
const CF_TYPE = { TEXT: '文本', NUMBER: '数值', DATE: '日期', BOOL: '布尔', SELECT: '下拉' }
const customFields = ref([])   // 启用字段（渲染用）
const cfDefs = ref([])         // 全部字段（配置页）
async function loadCustomFields() {
  try { customFields.value = await api.get('/custom-fields/active?objectType=ASSET') } catch (e) { customFields.value = [] }
}
const showCfConfig = ref(false)
const cfErr = ref('')
const cf = reactive({ fieldKey: '', label: '', dataType: 'TEXT', options: '', required: false })
async function openCfConfig() {
  cfErr.value = ''
  try { cfDefs.value = await api.get('/custom-fields?objectType=ASSET') } catch (e) { cfDefs.value = [] }
  showCfConfig.value = true
}
async function addCf() {
  cfErr.value = ''
  try {
    await api.post('/custom-fields', { orgId: af.orgId, objectType: 'ASSET', fieldKey: cf.fieldKey, label: cf.label, dataType: cf.dataType, options: cf.options || null, required: cf.required, sensitive: false, aggregatable: false, seq: cfDefs.value.length })
    Object.assign(cf, { fieldKey: '', label: '', dataType: 'TEXT', options: '', required: false })
    await openCfConfig(); await loadCustomFields()
  } catch (e) { cfErr.value = e.message }
}
async function retireCf(d) {
  try { await api.post('/custom-fields/' + d.id + '/retire', {}); await openCfConfig(); await loadCustomFields() }
  catch (e) { window.alert(e.message) }
}
/** B46：资产台账导出（当前列表态，含深化合规属性）。 */
function exportAssets() {
  const headers = ['ID', '资产名称', '类型', '责任人', '数据分级', '含个人信息', '跨境', '等保备案',
    '含持卡人数据', '重要性', '等保定级', '测评到期', 'CIA评级', '网络区域', '状态']
  const rows = assets.value.map((r) => [
    'AST-' + r.id, r.name, ASSET_TYPE[r.assetType] || r.assetType, r.owner || '',
    CLS_LABEL[r.classification] || r.classification, r.containsPi ? '是' : '否', r.crossBorder ? '是' : '否',
    r.mlpsFiled ? '已备案' : '', r.containsChd ? '是' : '否', CRIT_LABEL[r.criticality] || r.criticality || '',
    r.mlpsLevel ? r.mlpsLevel + '级' : '', r.mlpsReviewDue || '', r.ciaRating || '', r.networkZone || '', r.status
  ])
  exportCsv('资产台账_' + new Date().toISOString().slice(0, 10) + '.csv', headers, rows)
}

/** B47：等保测评到期 30 天内（或已逾期）标红。 */
function mlpsDueSoon(r) {
  if (!r.mlpsReviewDue) return false
  const diff = (new Date(r.mlpsReviewDue) - new Date()) / 86400000
  return diff <= 30
}
async function retireAsset(r) {
  if (!window.confirm(`确认退役资产「${r.name}」？退役后不再参与新评估的范围选择（留痕保档）。`)) return
  try { await api.post('/assets/' + r.id + '/retire', {}); await loadAssets() }
  catch (e) { window.alert(e.message) }
}

// ===== Tab3：个人信息处理活动 ROPA（真实后端 /api/ropa）=====
const ROPA_ST = { DRAFT: '草稿', ACTIVE: '生效', RETIRED: '已停用' }
const ropas = ref([])
async function loadRopas() {
  try { ropas.value = await api.get('/ropa') } catch (e) { ropas.value = [] }
}
const showRopa = ref(false)
const ropaSaving = ref(false)
const ropaErr = ref('')
const rf = reactive({ activityName: '', purpose: '', dataCategories: '', legalBasis: '', retention: '', processingMethod: '', recipients: '', crossBorder: false, orgId: 12 })
function openRopa() {
  Object.assign(rf, { activityName: '', purpose: '', dataCategories: '', legalBasis: '', retention: '', processingMethod: '', recipients: '', crossBorder: false, orgId: 12 })
  ropaErr.value = ''; showRopa.value = true
}
async function submitRopa() {
  ropaSaving.value = true; ropaErr.value = ''
  try {
    // B14：处理方式/接收方随登记提交（个保法§55/56 法定字段）
    await api.post('/ropa', { orgId: rf.orgId, activityName: rf.activityName, purpose: rf.purpose || null, dataCategories: rf.dataCategories || null, legalBasis: rf.legalBasis || null, crossBorder: rf.crossBorder, retention: rf.retention || null, processingMethod: rf.processingMethod || null, recipients: rf.recipients || null })
    showRopa.value = false; await loadRopas()
  } catch (e) { ropaErr.value = e.message } finally { ropaSaving.value = false }
}

onMounted(() => { loadTree(); loadAssets(); loadRopas(); loadCustomFields() })
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-org 区块及其依赖的全局 CSS。
   颜色一律走 tokens.css 语义令牌。
   主题表头底色规则用更具体的类名 .view-orgasset，避免污染其它页。
   ======================================================== */

/* ---- 页头 phead ---- */
.phead {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
}
.phead .kqt {
  font-size: 10.5px;
  letter-spacing: 1.5px;
  color: var(--accent);
  text-transform: uppercase;
  font-weight: 700;
  margin-bottom: 4px;
}
.phead h1 {
  font-size: 20px;
  font-weight: 760;
  letter-spacing: -0.3px;
  font-family: var(--font-display);
}
.phead .sp {
  flex: 1;
}

/* ---- 按钮 btn / ghost ---- */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  border: 0;
  border-radius: var(--radius-md);
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-1);
}
.btn.ghost {
  background: var(--surface);
  color: var(--text-2);
  border: 1px solid var(--surface-border);
}

/* ---- Tab 切换 tabbar / tabpane ---- */
.tabbar {
  display: inline-flex;
  gap: 2px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  padding: 3px;
  box-shadow: var(--shadow-1);
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.tabbar button {
  border: 0;
  background: none;
  padding: 6px 13px;
  font-size: 12px;
  color: var(--text-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-weight: 500;
}
.tabbar button.on {
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-weight: 600;
}

/* ---- 布局栅格 g / g-main-side / g-16-1 ---- */
.g {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
}
.g-main-side {
  grid-template-columns: 1fr 300px;
  align-items: start;
}
.g-16-1 {
  grid-template-columns: 1.6fr 1fr;
}
@media (max-width: 980px) {
  .g-main-side,
  .g-16-1 {
    grid-template-columns: 1fr;
  }
}

/* ---- KPI 卡片 kpibar.k4 ---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
}
.kpibar.k4 {
  grid-template-columns: repeat(4, 1fr);
}
.kc {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  padding: 13px 13px;
  box-shadow: var(--shadow-1);
}
.kc .l {
  font-size: 11px;
  color: var(--text-2);
}
.kc .v {
  font-size: 22px;
  font-weight: 790;
  font-family: var(--font-display);
  margin-top: 5px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.kc .v small {
  font-size: 12px;
  color: var(--text-3);
}

/* ---- 卡片 card / 卡头 ch / 卡体 cb ---- */
.card {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
  margin-bottom: 14px;
}
.ch {
  display: flex;
  align-items: center;
  padding: 14px 18px 4px;
}
.ch h3 {
  font-size: 14px;
  font-weight: 720;
  font-family: var(--font-display);
}
.ch .sub {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-3);
}
.ch .more {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--accent-strong);
  cursor: pointer;
}
/* 当同一卡头同时含 sub 与 more 时（ROPA 卡头），sub 取 margin-left:auto 占位，
   more 紧随其后不再 auto，保持原型「子标题 … 操作」并排在右侧的版式 */
.ch .sub + .more {
  margin-left: 12px;
}
.cb {
  padding: 14px 18px 18px;
}

/* ---- 组织树 tree / tn / dot2 / cnt（含 lv2、lv3 缩进层级）---- */
.tree .tn {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12.5px;
}
.tree .tn:hover {
  background: var(--accent-tint);
}
.tree .tn .dot2 {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: var(--accent);
}
.tree .lv2 {
  padding-left: 22px;
}
.tree .lv3 {
  padding-left: 44px;
  color: var(--text-3);
}
.tree .tn .cnt {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--text-3);
}

/* ---- AD 同步态势 sidecard ---- */
.sidecard {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
  padding: 15px;
  margin-bottom: 14px;
}
.sidecard h4 {
  font-size: 12.5px;
  font-weight: 700;
  margin-bottom: 10px;
}

/* ---- 信息行 srow ---- */
.srow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: 11.5px;
  color: var(--text-2);
}
.srow:last-child {
  border: 0;
}
.srow b {
  color: var(--text-1);
}

/* ---- 横向条 bars ---- */
.bars {
  display: flex;
  flex-direction: column;
  gap: 11px;
}
.bar-row .hd {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 12px;
}
.bar-row .hd .nm {
  color: var(--text-2);
}
.bar-row .hd b {
  font-weight: 700;
}
.track {
  height: 9px;
  background: rgba(120, 120, 120, 0.1);
  border-radius: 6px;
  display: flex;
  overflow: hidden;
  gap: 2px;
}
.seg2 {
  height: 100%;
  border-radius: 6px;
}
.seg2.a {
  background: var(--accent);
}

/* ---- 表格 table（对齐原型全局 table 规则）---- */
table {
  width: 100%;
  border-collapse: collapse;
}
thead th {
  text-align: left;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-3);
  padding: 0 18px 10px;
}
tbody td {
  padding: 11px 18px;
  border-top: 1px solid var(--border-subtle);
  font-size: 12px;
}
tbody tr {
  transition: background 0.15s;
}
tbody tr:hover {
  background: var(--accent-tint);
}

/* ---- 编号 code ---- */
.code {
  font-weight: 700;
  color: var(--accent-strong);
  font-variant-numeric: tabular-nums;
}

/* ---- 类型 / 分级标识 pill ---- */
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  background: rgba(120, 120, 120, 0.1);
  color: var(--text-2);
}
.pill.blue {
  background: var(--info-tint);
  color: var(--info);
}
.pill.violet {
  background: var(--plum-tint);
  color: var(--plum);
}

/* ---- 数据分级 / 重要性 / 敏感标签 tag ---- */
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 700;
}
.tag::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
}
.tag.h {
  background: var(--danger-tint);
  color: var(--danger);
}
.tag.h::before {
  background: var(--danger);
}
.tag.m {
  background: var(--warning-tint);
  color: #a87d22;
}
.tag.m::before {
  background: var(--warning);
}
.tag.l {
  background: var(--safe-weak);
  color: var(--safe);
}
.tag.l::before {
  background: var(--safe);
}

/* ---- 状态标签 st（AD 同步态势「正常」）---- */
.st {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-2);
}
.st .d {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.st.ok {
  color: var(--success);
}
.st.ok .d {
  background: var(--success);
}

/* ---- 朱砂 t-gov 表头底色（用更具体的 .view-orgasset 限定，避免污染其它页）---- */
:global(body.t-gov .view-orgasset thead th) {
  background: var(--accent-tint);
}

/* ---- 组织树手动配置 ---- */
.otree { display: flex; flex-direction: column; }
.orow { display: flex; align-items: center; gap: 8px; padding: 7px 4px; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }
.orow .onm { font-weight: 600; }
.orow .ocode { font-family: var(--font-mono, monospace); font-size: 11px; color: var(--accent-strong); }
.orow .opill { font-size: 10px; padding: 1px 7px; border-radius: 6px; background: var(--info-tint); color: var(--info); }
.orow .ospc { flex: 1; }
.orow .mini { width: 24px; height: 22px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; font-size: 12px; line-height: 1; }
.orow .mini:hover { background: var(--accent-tint); }
.orow .mini.danger:hover { color: var(--danger); border-color: var(--danger); }
.sidenote { font-size: 11.5px; color: var(--text-2); line-height: 1.6; margin: 0 0 10px; }
.ok-msg { color: var(--success); font-weight: 600; font-size: 12px; margin-top: 10px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 16px; text-align: center; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.chkrow { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; margin: 2px 0 12px; }
.chk { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: var(--text-2); }
.mutedcell { color: var(--text-2); max-width: 220px; }
/* B12 自定义字段 */
.cf-divider { font-size: 11px; font-weight: 700; color: var(--accent-strong); border-top: 1px dashed var(--border-subtle); margin: 6px 0 10px; padding-top: 10px; }
.cf-add { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 10px; background: var(--bg); border-radius: var(--radius-md); }
.cf-add input, .cf-add select { height: 30px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-sm); background: var(--surface); color: var(--text-1); font-size: 12px; font-family: inherit; }
</style>
