<!-- =============================================================
     合规态势驾驶舱主页（DashboardView）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-dashboard 区域
     1:1 复原，逐区块还原其 DOM 结构与内联 CSS：
       1) 页头 phead（标题 + 子公司分段切换 + 编辑布局/添加组件按钮）；
       2) KPI 指标卡组 kpibar（8 张卡，含进度条）；
       3) 可编辑大屏栅格 dgrid（12 列 span）：
          · 子公司 × 风险域 · 热力矩阵（等宽列 heat，span 6）
          · 整改完成率 · 分子公司（bars，span 3）
          · KRI 持续监控（kri + 迷你折线，span 3）
          · 体系合规达成度（bars，span 4）
          · 待我审批（worklist，span 4）
          · 重点关注 · 实时事件流（feed，span 4）
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌，
     热力矩阵单元格按原型保留其内联十六进制底色（红→绿渐变示例值）。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <!-- ===== 页头：标题 + 子公司分段 + 操作按钮 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('dash.overviewTag') }}</div>
          <h1>
            {{ $t('dash.title') }}
            <small>{{ $t('dash.subtitle', { t: nowText }) }}</small>
          </h1>
        </div>
        <div class="sp"></div>
        <!-- 子公司分段切换（默认全集团高亮，纯展示） -->
        <div class="seg">
          <button
            v-for="(s, i) in segs"
            :key="s"
            :class="{ on: i === activeSeg }"
            @click="activeSeg = i"
          >
            {{ s }}
          </button>
        </div>
        <button class="btn ghost" :class="{ on: editMode }" @click="editMode = !editMode">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
          </svg>
          <span class="lbl">{{ editMode ? '完成' : $t('dash.editLayout') }}</span>
        </button>
        <div class="addwrap">
          <button class="btn ghost" @click="showAdd = !showAdd">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14" />
            </svg>
            {{ $t('dash.addWidget') }}<span v-if="available.length" class="abadge">{{ available.length }}</span>
          </button>
          <div v-if="showAdd" class="addmenu" @click.self="showAdd = false">
            <div class="addmenu-pop">
              <div class="amh">添加可视化组件</div>
              <button v-for="c in available" :key="c.id" class="ami" @click="addW(c.id)">
                ＋ {{ catName(c) }}<span v-if="!c.builtin" class="extag">新</span>
              </button>
              <div v-if="!available.length" class="amempty">所有组件已在面板中。</div>
              <div class="amsep"></div>
              <button class="ami rst" @click="resetLayout(); showAdd = false">↺ 重置为默认布局</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== KPI 指标卡组（8 张） ===== -->
      <div class="kpibar">
        <div class="kc clk" v-for="(k, i) in kpis" :key="i" @click="drillKpi(k)" title="点击查看指标口径与构成">
          <div class="l">{{ $t('dash.kpi.' + k.key + '.l') }}</div>
          <div class="v" :style="k.vColor ? { color: k.vColor } : null">
            {{ k.v }}
            <small v-if="k.suffix">{{ k.suffix }}</small>
            <span v-if="k.delta" :class="k.deltaDir">{{ k.delta }}</span>
          </div>
          <div class="s">{{ $t('dash.kpi.' + k.key + '.s') }}</div>
          <div class="pb">
            <i :style="{ width: k.pct + '%', background: k.bar }"></i>
          </div>
        </div>
      </div>

      <!-- 诚实标注：KPI 卡/热力矩阵/整改完成率为真实后端聚合；KRI 折线与体系达成度仍为原型示意。 -->
      <div class="dash-note">{{ $t('dash.scaffoldNote') }}</div>

      <!-- 指标下钻弹层（需求 4.5.4：口径/构成/来源可解释）-->
      <div v-if="kpiDrill" class="modal-mask" @click.self="kpiDrill = null">
        <div class="modal-card">
          <h3>{{ $t('dash.kpi.' + kpiDrill.key + '.l') }} · 指标口径</h3>
          <div class="kd-v">当前值：<b :style="kpiDrill.vColor ? { color: kpiDrill.vColor } : null">{{ kpiDrill.v }}</b></div>
          <div class="kd-row"><span class="kd-k">口径</span>{{ (KPI_DOC[kpiDrill.key] || {}).formula || '—' }}</div>
          <div class="kd-row"><span class="kd-k">数据来源</span>{{ (KPI_DOC[kpiDrill.key] || {}).source || '—' }}</div>
          <div class="kd-row"><span class="kd-k">更新方式</span>实时（后端按您的可见组织范围汇总）</div>
          <div class="modal-actions">
            <button v-if="(KPI_DOC[kpiDrill.key] || {}).route" class="btn ghost" @click="goKpi(kpiDrill)">查看明细 →</button>
            <button class="btn" @click="kpiDrill = null">关闭</button>
          </div>
        </div>
      </div>

      <!-- ===== 可编辑大屏栅格（12 列） ===== -->
      <!-- #6 拖拽缩放实时长宽读数（跟随光标）-->
      <div v-if="resizeInfo.show" class="resize-badge" :style="{ left: resizeInfo.x + 16 + 'px', top: resizeInfo.y + 16 + 'px' }">
        跨 <b>{{ resizeInfo.cols }}</b>/12 列 · <b>{{ resizeInfo.w }}</b>×<b>{{ resizeInfo.h }}</b> px
      </div>

      <div class="dgrid" :class="{ editing: editMode }">
        <!-- 子公司 × 风险域 · 热力矩阵（span 6 · 等宽列） -->
        <div v-if="inLayout('heat')" class="card gi" :style="{ '--w': widthOf('heat') }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('heat') === w }" @click="setWidth('heat', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('heat')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('heat', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch">
            <h3>{{ $t('dash.heat.title') }}</h3>
            <span class="sub">{{ $t('dash.heat.sub') }}</span>
          </div>
          <div class="cb">
            <table class="heat">
              <thead>
                <tr>
                  <th></th>
                  <th v-for="d in heatDomains" :key="d">{{ $t('dash.heat.domain.' + d) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in heatRows" :key="row.name">
                  <td class="lbl">{{ row.name }}</td>
                  <td
                    v-for="(cell, ci) in row.cells"
                    :key="ci"
                    class="cell"
                    :style="{ background: cell.c }"
                  >
                    {{ cell.v }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- B37 跨子公司 benchmark（仅集团/母公司多组织视角显示，静态整行） -->
        <div v-if="showBenchmark" class="card gi" style="grid-column:1/-1">
          <div class="ch">
            <h3>跨子公司 Benchmark</h3>
            <span class="sub">合规负荷横向对比 · 集团均值 {{ benchmark.avgLoad }} · 通知回执率</span>
          </div>
          <div class="cb">
            <table class="bm">
              <thead><tr><th>排名</th><th style="text-align:left">子公司</th><th>合规负荷</th><th>对均值</th><th>回执率</th><th>回执台账</th></tr></thead>
              <tbody>
                <tr v-for="r in benchmark.rows" :key="r.orgId">
                  <td><b>#{{ r.rank }}</b></td>
                  <td style="text-align:left">{{ r.orgName }}</td>
                  <td>{{ r.load }}</td>
                  <td :class="r.deltaVsAvg > 0 ? 'bm-hi' : (r.deltaVsAvg < 0 ? 'bm-lo' : '')">{{ r.deltaVsAvg > 0 ? '+' + r.deltaVsAvg : r.deltaVsAvg }}</td>
                  <td :class="r.ackRatePct >= 85 ? 'bm-lo' : (r.ackRatePct >= 60 ? '' : 'bm-hi')">{{ r.ackRatePct }}%</td>
                  <td style="color:var(--text-3)">{{ r.ackDone }}/{{ r.ackTotal }}</td>
                </tr>
              </tbody>
            </table>
            <p style="font-size:11px;color:var(--text-3);margin:8px 0 0">合规负荷 = 六域未闭环计数之和（负荷重者排名靠前）。回执口径：回执 = 收件人在通知中心点「确认收到」（提醒台账 read_by 落人），企微/邮件/短信外推不计入回执；回执率低 = 提醒到人未被确认。</p>
          </div>
        </div>

        <!-- 整改完成率 · 分子公司（span 3） -->
        <div v-if="inLayout('remed')" class="card gi" :style="{ '--w': widthOf('remed') }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('remed') === w }" @click="setWidth('remed', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('remed')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('remed', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch">
            <h3>{{ $t('dash.remed.title') }}</h3>
          </div>
          <div class="cb">
            <div class="bars">
              <div class="bar-row" v-for="r in remediation" :key="r.name">
                <div class="hd">
                  <span class="nm">{{ r.name }}</span>
                  <span>
                    <b>{{ r.pct }}%</b>
                    <span v-if="r.overdue" class="ov">{{ $t('dash.overdue', { n: r.overdue }) }}</span>
                  </span>
                </div>
                <div class="track">
                  <div class="seg2" :class="r.tone" :style="{ width: r.pct + '%' }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- KRI 持续监控（span 3 · 含迷你折线） -->
        <div v-if="inLayout('kri')" class="card gi" :style="{ '--w': widthOf('kri') }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('kri') === w }" @click="setWidth('kri', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('kri')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('kri', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch">
            <h3>{{ $t('dash.kri.title') }}</h3>
            <span class="sub">{{ $t('dash.kri.sub') }}</span>
          </div>
          <!-- 七轮 7-5：接真值——KRI 指标与最新测量来自 /api/kris，不再展示写死的示意折线 -->
          <div class="cb">
            <div class="kri">
              <div class="ki" v-for="k in kris" :key="k.id">
                <span class="dt" :style="{ background: kriDot(k.currentStatus) }"></span>
                <span class="nm">
                  <div class="t">{{ k.name }}</div>
                  <div class="src">{{ k.code }}</div>
                </span>
                <!-- B37：迷你折线趋势（近 8 次测量）+ 预警阈值基线 -->
                <svg v-if="kriSpark[k.id]" class="kspark" :viewBox="'0 0 ' + 54 + ' ' + 18" preserveAspectRatio="none">
                  <line v-if="kriSpark[k.id].baseY" x1="0" :y1="kriSpark[k.id].baseY" x2="54" :y2="kriSpark[k.id].baseY" stroke="var(--warning)" stroke-width="0.6" stroke-dasharray="2 2" opacity="0.6" />
                  <polyline :points="kriSpark[k.id].line" fill="none" :stroke="kriDot(k.currentStatus)" stroke-width="1.3" stroke-linejoin="round" stroke-linecap="round" />
                </svg>
                <span class="val" :style="{ color: kriDot(k.currentStatus) }">
                  {{ k.currentValue == null ? '—' : k.currentValue + (k.unit || '') }}
                  <div class="th">预警 {{ k.thresholdWarning }} / 严重 {{ k.thresholdCritical }}</div>
                </span>
              </div>
              <div v-if="!kris.length" class="ki" style="color: var(--text-3); font-size: 12px">
                暂无 KRI 指标——到 风险评估 · KRI 监控 定义指标并录入测量值。
              </div>
            </div>
          </div>
        </div>

        <!-- 体系合规达成度（span 4） -->
        <div v-if="inLayout('frame')" class="card gi" :style="{ '--w': widthOf('frame') }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('frame') === w }" @click="setWidth('frame', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('frame')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('frame', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch">
            <h3>{{ $t('dash.frame.title') }}</h3>
            <span class="sub">{{ $t('dash.frame.sub') }}</span>
          </div>
          <!-- 七轮 7-5：原各体系百分比为写死示意——控制点覆盖率计算尚未实现，诚实置灰待接入 -->
          <div class="cb" style="opacity: .75">
            <div style="font-size: 12px; color: var(--text-3); line-height: 1.8; padding: 6px 0">
              体系达成度需要「控制点覆盖率」真值计算（模板条款 × 评估结果聚合），该能力尚未实现，
              暂不展示示意百分比。八大体系模板与统一控件库已就绪，覆盖率计算随后续批次交付。
            </div>
          </div>
        </div>

        <!-- 待我审批（span 4） -->
        <div v-if="inLayout('approve')" class="card gi" :style="{ '--w': widthOf('approve') }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('approve') === w }" @click="setWidth('approve', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('approve')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('approve', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch">
            <h3>{{ $t('dash.approve.title') }}</h3>
            <span class="more">{{ $t('dash.approve.all', { n: approvals.length }) }}</span>
          </div>
          <!-- 七轮 7-5（A30）：接真值——Flowable 我的审批任务（/api/workbench/my-approvals） -->
          <div class="cb">
            <div class="wl">
              <div class="wi" v-for="a in approvals" :key="a.taskId">
                <span class="tp2">{{ BIZ_LABEL[a.bizType] || a.bizType }}</span>
                <div class="ti">
                  <div class="t">{{ (BIZ_LABEL[a.bizType] || a.bizType) + ' #' + a.bizId }}</div>
                  <div class="m">{{ a.nodeName || '待审批' }} · {{ a.roleGroup || '' }}</div>
                </div>
                <span class="due">{{ agoText(a.createdMs) }}</span>
              </div>
              <div v-if="!approvals.length" style="font-size: 12px; color: var(--text-3); padding: 8px 0">
                暂无待我审批的任务。
              </div>
            </div>
          </div>
        </div>

        <!-- 重点关注 · 实时事件流（span 4） -->
        <div v-if="inLayout('feed')" class="card gi" :style="{ '--w': widthOf('feed'), padding: 0 }">
          <div v-if="editMode" class="wedit" style="top: 8px; right: 10px">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: widthOf('feed') === w }" @click="setWidth('feed', w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW('feed')">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize('feed', $event)" title="拖拽自由调整宽度"></div>
          <div class="ch" style="padding: 14px 18px 8px">
            <h3>{{ $t('dash.feed.title') }}</h3>
          </div>
          <!-- 七轮 7-5（A30）：接真值——最近调度提醒/规则告警（/api/workbench/notifications） -->
          <div class="feed">
            <div class="it" v-for="f in feed" :key="f.id">
              <span class="tm">{{ new Date(f.createdAtMs).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }}</span>
              <span class="bd" :class="feedBadge(f.eventType)">{{ feedBadgeText(f.eventType) }}</span>
              <span class="tx">{{ f.message || (f.eventType + ' · ' + f.objectType + ':' + f.objectId) }}</span>
            </div>
            <div v-if="!feed.length" class="it" style="color: var(--text-3)">暂无事件——调度内核与规则引擎产出的提醒会实时汇入。</div>
          </div>
        </div>

        <!-- ===== 添加的额外可视化组件（大小可调 / 可移除）===== -->
        <div v-for="e in placedExtras" :key="e.id" class="card gi" :style="{ '--w': e.w }">
          <div v-if="editMode" class="wedit">
            <button v-for="w in WIDTHS" :key="w" class="wsz" :class="{ on: e.w === w }" @click="setWidth(e.id, w)">{{ WLABEL[w] }}</button>
            <button class="wsz rm" @click="removeW(e.id)">移除</button>
          </div>
          <div v-if="editMode" class="wgrip" @mousedown="startResize(e.id, $event)" title="拖拽自由调整宽度"></div>
          <div class="ch"><h3>{{ extraName(e.id) }}</h3><span class="sub">真值 · 实时接口</span></div>
          <div class="cb">
            <!-- 残余风险等级分布 -->
            <div v-if="e.id === 'ex-risk'" class="exbars">
              <div v-for="b in exRisk" :key="b.l" class="exbar-row"><span class="exl">{{ b.l }}</span><div class="extrack"><i :style="{ width: b.w, background: b.c }"></i></div><b>{{ b.v }}</b></div>
              <div v-if="!exRisk.length" class="exempty">暂无风险发现</div>
            </div>
            <!-- 组织 × 六域构成条 -->
            <div v-else-if="e.id === 'ex-orgbar'">
              <div v-for="o in exOrgBar" :key="o.name" class="orgbar-row">
                <div class="hd"><span class="nm">{{ o.name }}</span><b>{{ o.total }}</b></div>
                <div class="orgbar-track"><i v-for="(s, i) in o.segs" :key="i" :style="{ width: s.w, background: s.c }" :title="s.l + ' ' + s.v"></i></div>
              </div>
              <div class="orgbar-legend"><span v-for="[k, l, c] in ORGBAR_DOMAINS" :key="k"><i :style="{ background: c }"></i>{{ l }}</span></div>
            </div>
            <!-- 整改逾期排行 -->
            <div v-else-if="e.id === 'ex-remedrank'">
              <div v-for="r in exRemedRank" :key="r.orgId" class="rankrow">
                <span class="nm">{{ r.orgName }}</span>
                <span class="ov" v-if="r.remedOverdue">逾期 {{ r.remedOverdue }}</span>
                <b style="margin-left:auto">{{ r.remedPct }}%</b>
              </div>
              <div v-if="!exRemedRank.length" class="exempty">暂无整改数据</div>
            </div>
            <!-- 供应商风险分布 -->
            <div v-else-if="e.id === 'ex-vendor'" class="exbars">
              <div v-for="b in exVendor" :key="b.l" class="exbar-row"><span class="exl">{{ b.l }}</span><div class="extrack"><i :style="{ width: b.w, background: b.c }"></i></div><b>{{ b.v }}</b></div>
              <div v-if="!exVendor.length" class="exempty">暂无供应商</div>
            </div>
            <!-- 报送临近到期 -->
            <div v-else-if="e.id === 'ex-filing'">
              <div v-for="f in exFiling" :key="f.id" class="rankrow">
                <span class="code2">#{{ f.id }}</span><span class="nm">{{ f.title }}</span>
                <span class="fdate">{{ f.due }}</span>
                <span class="ftag" :class="f.cls">{{ f.days < 0 ? ('逾期 ' + (-f.days) + ' 天') : ('剩 ' + f.days + ' 天') }}</span>
              </div>
              <div v-if="!exFiling.length" class="exempty">无未报送事项</div>
            </div>
            <!-- 通知回执率 -->
            <div v-else-if="e.id === 'ex-ack'" class="exdonut">
              <svg width="96" height="96" viewBox="0 0 42 42"><g transform="rotate(-90 21 21)">
                <circle cx="21" cy="21" r="15.9" fill="none" stroke="rgba(120,120,120,.14)" stroke-width="5" />
                <circle cx="21" cy="21" r="15.9" pathLength="100" fill="none" stroke="var(--success)" stroke-width="5" :stroke-dasharray="exAck.pct + ' ' + (100 - exAck.pct)" stroke-linecap="round" />
              </g></svg>
              <div class="exd-c"><b>{{ exAck.pct }}%</b><span>{{ exAck.acked }}/{{ exAck.total }} 已回执</span></div>
            </div>
            <!-- 制度生命周期漏斗 -->
            <div v-else-if="e.id === 'ex-policy'" class="exbars">
              <div v-for="b in exPolicy" :key="b.l" class="exbar-row"><span class="exl">{{ b.l }}</span><div class="extrack"><i :style="{ width: b.w, background: b.c }"></i></div><b>{{ b.v }}</b></div>
              <div v-if="!exPolicy.length" class="exempty">暂无制度</div>
            </div>
            <!-- 评估进度分布 -->
            <div v-else-if="e.id === 'ex-assess'" class="exbars">
              <div v-for="b in exAssess" :key="b.l" class="exbar-row"><span class="exl">{{ b.l }}</span><div class="extrack"><i :style="{ width: b.w, background: b.c }"></i></div><b>{{ b.v }}</b></div>
              <div v-if="!exAssess.length" class="exempty">暂无评估</div>
            </div>
            <!-- 合规义务落实率 -->
            <div v-else-if="e.id === 'ex-compliance'" class="exdonut">
              <svg width="96" height="96" viewBox="0 0 42 42"><g transform="rotate(-90 21 21)">
                <circle cx="21" cy="21" r="15.9" fill="none" stroke="rgba(120,120,120,.14)" stroke-width="5" />
                <circle cx="21" cy="21" r="15.9" pathLength="100" fill="none" stroke="var(--accent)" stroke-width="5" :stroke-dasharray="exObl.pct + ' ' + (100 - exObl.pct)" stroke-linecap="round" />
              </g></svg>
              <div class="exd-c"><b>{{ exObl.pct }}%</b><span>{{ exObl.done }}/{{ exObl.total }} 已落实</span></div>
            </div>
            <!-- KRI 状态计数 -->
            <div v-else-if="e.id === 'ex-kri'" class="kricnt">
              <div class="kc2 ok"><b>{{ exKri.NORMAL }}</b><span>正常</span></div>
              <div class="kc2 warn"><b>{{ exKri.WARNING }}</b><span>预警</span></div>
              <div class="kc2 crit"><b>{{ exKri.CRITICAL }}</b><span>严重</span></div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
// 驾驶舱主页：视觉/样式 1:1 取自原型；KPI 指标卡接 /api/dashboard/summary，
// 热力矩阵/整改完成率接 /api/dashboard/org-summary（按组织聚合真值）；KRI 折线与体系达成度仍为示意。
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs } from '@/orgs.js'

const { t } = useI18n()
// 真实组织（消灭原型假子公司 数科/保理）：分段维度由真实组织树驱动。
const orgList = useOrgs()
const subsidiaries = computed(() => orgList.value.filter((o) => o.parentId === 1)) // 集团直属子公司

// ---- 子公司分段切换（页头右侧 seg）----
const segs = computed(() => ['全集团', ...subsidiaries.value.map((o) => o.name)])
const activeSeg = ref(0)

// ===== 大屏布局编辑：组件目录 + 布局（大小可调、可增删，localStorage 持久化）=====
// builtin=true 的是页面已有 6 个组件；false 的是「添加组件」可加入的额外可视化。
const CATALOG = [
  { id: 'heat', k: 'dash.heat.title', w: 6, builtin: true },
  { id: 'remed', k: 'dash.remed.title', w: 3, builtin: true },
  { id: 'kri', k: 'dash.kri.title', w: 3, builtin: true },
  { id: 'frame', k: 'dash.frame.title', w: 4, builtin: true },
  { id: 'approve', k: 'dash.approve.title', w: 4, builtin: true },
  { id: 'feed', k: 'dash.feed.title', w: 4, builtin: true },
  // ---- 可添加的真值可视化组件（数据全部来自真实后端接口，无示意值）----
  { id: 'ex-risk', label: '残余风险等级分布', w: 3, builtin: false },
  { id: 'ex-orgbar', label: '组织 × 六域构成条', w: 4, builtin: false },
  { id: 'ex-remedrank', label: '整改逾期排行', w: 3, builtin: false },
  { id: 'ex-vendor', label: '供应商风险分布', w: 3, builtin: false },
  { id: 'ex-filing', label: '报送临近到期', w: 4, builtin: false },
  { id: 'ex-ack', label: '通知回执率（30 天）', w: 3, builtin: false },
  { id: 'ex-policy', label: '制度生命周期漏斗', w: 3, builtin: false },
  { id: 'ex-assess', label: '评估进度分布', w: 3, builtin: false },
  { id: 'ex-compliance', label: '合规义务落实率', w: 3, builtin: false },
  { id: 'ex-kri', label: 'KRI 状态计数', w: 3, builtin: false }
]
const DEFAULT_LAYOUT = CATALOG.filter((c) => c.builtin).map((c) => ({ id: c.id, w: c.w }))
const LKEY = 'grc-dash-layout'
function loadLayout() {
  try { const s = JSON.parse(localStorage.getItem(LKEY)); return Array.isArray(s) && s.length ? s : DEFAULT_LAYOUT.slice() }
  catch (e) { return DEFAULT_LAYOUT.slice() }
}
const editMode = ref(false)
const showAdd = ref(false)
const layout = ref(loadLayout())
function persist() { localStorage.setItem(LKEY, JSON.stringify(layout.value)) }
function entry(id) { return layout.value.find((e) => e.id === id) }
function inLayout(id) { return !!entry(id) }
function widthOf(id) { return (entry(id) || {}).w || 4 }
const WIDTHS = [3, 4, 6, 12]
const WLABEL = { 3: '窄', 4: '中', 6: '宽', 12: '整行' }
function setWidth(id, w) { const e = entry(id); if (e) { e.w = Math.min(12, Math.max(2, w)); layout.value = [...layout.value]; persist() } }
function removeW(id) { layout.value = layout.value.filter((e) => e.id !== id); persist() }

// ===== 自由缩放：拖拽组件右/下边手柄连续改变 列宽(2~12) =====
// 12 列响应式栅格，列宽随分辨率自适应；拖拽按"当前栅格列宽"换算成跨列数并即时套用。
let resizing = null
// 拖拽时的实时长宽读数（跟随光标显示 跨列数 + 像素长宽；栅格实时重排即所见即所得）
const resizeInfo = ref({ show: false, cols: 0, w: 0, h: 0, x: 0, y: 0 })
function startResize(id, e) {
  e.preventDefault(); e.stopPropagation()
  const grid = e.target.closest('.dgrid')
  const cardEl = e.target.closest('.card')
  if (!grid) return
  const gap = 14
  const gridW = grid.getBoundingClientRect().width
  const colW = (gridW - gap * 11) / 12 + gap   // 每列有效宽（含一个间隙）
  resizing = { id, startX: e.clientX, startW: widthOf(id), colW, cardEl }
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', onResize)
  window.addEventListener('mouseup', endResize)
}
function onResize(e) {
  if (!resizing) return
  const deltaCols = Math.round((e.clientX - resizing.startX) / resizing.colW)
  setWidth(resizing.id, resizing.startW + deltaCols)
  // 栅格已即时重排；下一帧读取该组件渲染后的真实长宽，实时反馈给用户
  requestAnimationFrame(() => {
    if (!resizing || !resizing.cardEl) return
    const r = resizing.cardEl.getBoundingClientRect()
    resizeInfo.value = { show: true, cols: widthOf(resizing.id), w: Math.round(r.width), h: Math.round(r.height), x: e.clientX, y: e.clientY }
  })
}
function endResize() {
  resizing = null
  resizeInfo.value = { ...resizeInfo.value, show: false }
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('mousemove', onResize)
  window.removeEventListener('mouseup', endResize)
  persist()
}
function addW(id) { const c = CATALOG.find((x) => x.id === id); if (c && !inLayout(id)) { layout.value = [...layout.value, { id, w: c.w }]; persist(); showAdd.value = false } }
const catName = (c) => (c.builtin ? t(c.k) : c.label)
const available = computed(() => CATALOG.filter((c) => !inLayout(c.id)))            // 可添加（含被移除的内置 + 未放置的额外）
const placedExtras = computed(() => layout.value.filter((e) => { const c = CATALOG.find((x) => x.id === e.id); return c && !c.builtin }))
function extraName(id) { const c = CATALOG.find((x) => x.id === id); return c ? c.label : id }
function resetLayout() { layout.value = DEFAULT_LAYOUT.slice(); persist() }
// ===== 额外可视化组件的真值数据（全部来自真实后端接口）=====
const exData = reactive({ riskLevels: {}, vendors: [], filings: [], policies: [], assessments: [], obligations: [], kris: [], digest: [] })
async function loadExtras() {
  const safe = (p) => p.catch(() => null)
  const [rl, vd, fl, pl, as, ob, kr, dg] = await Promise.all([
    safe(api.get('/dashboard/risk-levels')), safe(api.get('/vendors')), safe(api.get('/reg-filings')),
    safe(api.get('/policies')), safe(api.get('/assessments')), safe(api.get('/obligations')),
    safe(api.get('/kris')), safe(api.get('/workbench/digest?days=30'))
  ])
  exData.riskLevels = rl || {}
  exData.vendors = vd || []
  exData.filings = fl || []
  exData.policies = pl || []
  exData.assessments = as || []
  exData.obligations = ob || []
  exData.kris = kr || []
  exData.digest = dg || []
}
const LV_ORDER = ['VERY_HIGH', 'HIGH', 'MID', 'LOW', 'VERY_LOW', 'UNSET']
const LV_ZH = { VERY_HIGH: '极高', HIGH: '高', MID: '中', LOW: '低', VERY_LOW: '极低', UNSET: '未定级' }
const LV_COLOR = { VERY_HIGH: '#7a1620', HIGH: 'var(--danger)', MID: '#a87d22', LOW: 'var(--safe, #4a8)', VERY_LOW: 'var(--accent-bright, var(--accent))', UNSET: 'var(--text-3)' }
// 残余风险等级分布（真值柱）
const exRisk = computed(() => {
  const max = Math.max(1, ...Object.values(exData.riskLevels))
  return LV_ORDER.filter((k) => exData.riskLevels[k]).map((k) => ({
    l: LV_ZH[k], v: exData.riskLevels[k], w: Math.round(exData.riskLevels[k] * 100 / max) + '%', c: LV_COLOR[k]
  }))
})
// 供应商风险分布（真值柱，含未评估）
const exVendor = computed(() => {
  const cnt = {}
  exData.vendors.forEach((v) => { const k = v.riskLevel || 'UNSET'; cnt[k] = (cnt[k] || 0) + 1 })
  const max = Math.max(1, ...Object.values(cnt))
  return LV_ORDER.filter((k) => cnt[k]).map((k) => ({
    l: k === 'UNSET' ? '未评估' : LV_ZH[k], v: cnt[k], w: Math.round(cnt[k] * 100 / max) + '%', c: LV_COLOR[k]
  }))
})
// 报送临近到期（未报送按期限升序 top6）
const exFiling = computed(() => exData.filings
  .filter((f) => f.status !== 'SUBMITTED' && f.status !== 'CLOSED' && f.statutoryDeadline)
  .sort((a, b) => (a.statutoryDeadline < b.statutoryDeadline ? -1 : 1)).slice(0, 6)
  .map((f) => {
    const days = Math.ceil((new Date(f.statutoryDeadline) - new Date()) / 86400000)
    return { id: f.id, title: f.title, due: f.statutoryDeadline, days, cls: days < 0 ? 'over' : (days <= 7 ? 'warn' : '') }
  }))
// 通知回执率（30 天）
const exAck = computed(() => {
  const total = exData.digest.reduce((s, d) => s + d.total, 0)
  const unread = exData.digest.reduce((s, d) => s + d.unread, 0)
  return { total, acked: total - unread, pct: total ? Math.round((total - unread) * 100 / total) : 100 }
})
// 制度生命周期漏斗
const exPolicy = computed(() => {
  const cnt = {}
  exData.policies.forEach((p) => { cnt[p.status] = (cnt[p.status] || 0) + 1 })
  const order = [['DRAFT', '草稿'], ['REVIEW', '评审中'], ['EFFECTIVE', '已生效'], ['DEPRECATED', '已废止']]
  const max = Math.max(1, ...Object.values(cnt))
  return order.filter(([k]) => cnt[k]).map(([k, l]) => ({ l, v: cnt[k], w: Math.round(cnt[k] * 100 / max) + '%', c: k === 'EFFECTIVE' ? 'var(--success)' : (k === 'REVIEW' ? '#a87d22' : 'var(--text-3)') }))
})
// 评估进度分布
const exAssess = computed(() => {
  const cnt = {}
  exData.assessments.forEach((a) => { cnt[a.status] = (cnt[a.status] || 0) + 1 })
  const order = [['DRAFT', '草稿'], ['IN_PROGRESS', '评估中'], ['PENDING_REVIEW', '待复核'], ['COMPLETED', '已完成']]
  const max = Math.max(1, ...Object.values(cnt))
  return order.filter(([k]) => cnt[k]).map(([k, l]) => ({ l, v: cnt[k], w: Math.round(cnt[k] * 100 / max) + '%', c: k === 'COMPLETED' ? 'var(--success)' : 'var(--accent)' }))
})
// 合规义务落实率（环形真值）
const exObl = computed(() => {
  const total = exData.obligations.length
  const done = exData.obligations.filter((o) => o.status === 'FULFILLED').length
  return { total, done, pct: total ? Math.round(done * 100 / total) : 0 }
})
// KRI 状态计数
const exKri = computed(() => {
  const cnt = { NORMAL: 0, WARNING: 0, CRITICAL: 0 }
  exData.kris.forEach((k) => { if (cnt[k.currentStatus] != null) cnt[k.currentStatus]++ })
  return cnt
})
// 整改逾期排行（org-summary 真值，逾期降序）
const exRemedRank = computed(() => [...orgSummary.value]
  .filter((r) => r.remedTotal > 0 || r.remedOverdue > 0)
  .sort((a, b) => b.remedOverdue - a.remedOverdue || a.remedPct - b.remedPct))
// 组织 × 六域构成条（org-summary 真值分段条）
const ORGBAR_DOMAINS = [
  ['risk', '风险', 'var(--danger)'], ['data', '数据', '#a87d22'], ['vendor', '第三方', 'var(--plum, #96c)'],
  ['reg', '监管', 'var(--warning, #c90)'], ['audit', '审计', 'var(--info, #47c)'], ['remed', '整改', 'var(--accent)']
]
const exOrgBar = computed(() => orgSummary.value.map((r) => {
  const total = ORGBAR_DOMAINS.reduce((s, [k]) => s + r[k], 0)
  return { name: r.orgName, total, segs: ORGBAR_DOMAINS.map(([k, l, c]) => ({ l, c, v: r[k], w: total ? (r[k] * 100 / total) + '%' : '0%' })) }
}))

// ---- 合规态势汇总（真实后端：跨模块按可见组织计数）----
const summary = ref(null)
const loadError = ref('')
onMounted(async () => {
  loadOrgSummary()
  loadBenchmark()
  loadExtras()
  try {
    summary.value = await api.get('/dashboard/summary')
  } catch (e) {
    loadError.value = e.message
  }
})
const dv = (x) => (summary.value ? x : '—')        // 未加载到则诚实显「—」
const pct = (x) => (x == null ? 0 : Math.min(x * 12, 100)) // 进度条宽度（装饰，按计数缩放封顶）

// ---- KPI 指标卡组（8 张，保留原型卡片视觉；数值全部接真实汇总）----
const kpis = computed(() => {
  const r = summary.value?.risk || {}
  const a = summary.value?.audit || {}
  const g = summary.value?.regulatory || {}
  const p = summary.value?.policy || {}
  const m = summary.value?.permission || {}
  return [
    { key: 'openRisk', v: dv(r.openFindings), bar: 'var(--accent)', pct: pct(r.openFindings) },
    { key: 'gated', v: dv(r.gatedFindings), vColor: 'var(--danger)', bar: 'var(--danger)', pct: pct(r.gatedFindings) },
    { key: 'kriWarn', v: dv(r.kriWarning), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(r.kriWarning) },
    { key: 'kriCrit', v: dv(r.kriCritical), vColor: 'var(--danger)', bar: 'var(--danger)', pct: pct(r.kriCritical) },
    { key: 'openAudit', v: dv(a.openFindings), bar: 'var(--accent)', pct: pct(a.openFindings) },
    { key: 'pendingFiling', v: dv(g.pendingFilings), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(g.pendingFilings) },
    { key: 'effPolicy', v: dv(p.effective), vColor: 'var(--info)', bar: 'var(--info)', pct: pct(p.effective) },
    { key: 'pendingSod', v: dv(m.pendingSodExceptions), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(m.pendingSodExceptions) }
  ]
})

// ===== 指标下钻（需求 4.5.4：可解释——口径/构成/来源）=====
const KPI_DOC = {
  openRisk: { formula: '风险发现中状态 ≠ VERIFIED 的数量（OPEN + IN_TREATMENT + DONE 待验证）', source: 'M2 风险评估 · risk_finding', route: '/risk' },
  gated: { formula: '残余等级为 高/极高 且无有效风险接受的发现数（CR-002 关闭门控命中）', source: 'M2 风险评估 · risk_finding + risk_acceptance', route: '/risk' },
  kriWarn: { formula: '当前值越过预警阈值(warning)的 KRI 指标数', source: 'M2 · kri_metric', route: '/risk' },
  kriCrit: { formula: '当前值越过严重阈值(critical)的 KRI 指标数', source: 'M2 · kri_metric', route: '/risk' },
  openAudit: { formula: '内外审发现中未闭环（无 VERIFIED 整改单）的数量', source: 'M3 审计管理 · audit_finding + remediation_order', route: '/internal-audit' },
  pendingFiling: { formula: '监管报送中状态 ≠ 已报送 的事项数', source: 'M11 监管事项 · reg_filing', route: '/regulatory-affairs' },
  effPolicy: { formula: '状态 = EFFECTIVE（已生效）的制度数', source: 'M1 制度体系 · policy', route: '/policy' },
  pendingSod: { formula: '状态 = PENDING（待审批）的 SoD 例外申请数', source: 'M8 权限 · sod_exception', route: '/permission' }
}
const kpiDrill = ref(null)
function drillKpi(k) { kpiDrill.value = k }
function goKpi(k) { const r = (KPI_DOC[k.key] || {}).route; if (r) { kpiDrill.value = null; window.location.hash = '#' + r } }

// ---- 热力矩阵：六域真值计数（后端 /api/dashboard/org-summary，按组织聚合）----
// 值 = 该组织该域的未决事项数；色阶按计数分档（0 绿 → 越多越红），不再是示意值。
const heatDomains = ['risk', 'data', 'vendor', 'reg', 'audit', 'remed']
const orgSummary = ref([])
async function loadOrgSummary() {
  try { orgSummary.value = await api.get('/dashboard/org-summary') } catch (e) { orgSummary.value = [] }
}
// B37 跨子公司 benchmark：仅当可见组织 >1（集团/母公司视角）才有横向对比意义
const benchmark = ref(null)
async function loadBenchmark() {
  try { benchmark.value = await api.get('/dashboard/benchmark') } catch (e) { benchmark.value = null }
}
const showBenchmark = computed(() => !!benchmark.value && benchmark.value.rows && benchmark.value.rows.length > 1)
function heatColor(v) {
  if (v <= 0) return '#7fa76a'
  if (v <= 2) return '#dfb84d'
  if (v <= 5) return '#e0a93f'
  if (v <= 9) return '#cf6233'
  return '#c0392b'
}
const heatRows = computed(() => orgSummary.value.map((r) => ({
  name: r.orgName,
  cells: heatDomains.map((d) => ({ v: r[d], c: heatColor(r[d]) }))
})))

// ---- 整改完成率 · 分组织（真值：VERIFIED/总数，逾期=过期未验证）----
const remediation = computed(() => orgSummary.value
  .filter((r) => r.remedTotal > 0 || r.remedOverdue > 0)
  .map((r) => ({
    name: r.orgName, pct: r.remedPct, overdue: r.remedOverdue,
    tone: r.remedPct >= 85 ? 'g' : (r.remedPct >= 70 ? 'm' : 'h')
  })))

// ===== 七轮 7-5（A3/A30）：四个曾写死示意数的组件接真值 =====

// 数据截至：页面加载时刻（准实时口径——各卡数据即时拉取）
const nowText = new Date().toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

// KRI 持续监控 ← /api/kris（指标定义+最新测量值+触阈状态）
const kris = ref([])
const kriDot = (st) => st === 'CRITICAL' ? 'var(--danger)' : (st === 'WARNING' ? 'var(--warning)'
  : (st === 'NORMAL' ? 'var(--success)' : 'var(--text-3)'))

// B37：KRI 迷你折线趋势——近 N 次测量归一化为 54×18 sparkline，叠加预警阈值基线
const SPARK_W = 54
const SPARK_H = 18
const kriSpark = ref({})   // { [kriId]: { line, baseY } }
async function loadKriSparks() {
  const map = {}
  await Promise.all(kris.value.map(async (k) => {
    try {
      const ms = await api.get('/kris/' + k.id + '/measurements')
      const pts = (ms || []).slice(-8).map((m) => Number(m.value)).filter((v) => !Number.isNaN(v))
      if (pts.length < 2) return
      // 值域含阈值，保证基线落在框内
      const wt = Number(k.thresholdWarning)
      const all = Number.isNaN(wt) ? pts : pts.concat(wt)
      const lo = Math.min(...all), hi = Math.max(...all)
      const span = hi - lo || 1
      const x = (i) => (i / (pts.length - 1)) * (SPARK_W - 2) + 1
      const y = (v) => SPARK_H - 1 - ((v - lo) / span) * (SPARK_H - 2)
      map[k.id] = {
        line: pts.map((v, i) => x(i).toFixed(1) + ',' + y(v).toFixed(1)).join(' '),
        baseY: Number.isNaN(wt) ? null : y(wt).toFixed(1)
      }
    } catch (e) { /* 该 KRI 无测量则不画 */ }
  }))
  kriSpark.value = map
}

// 待我审批 ← /api/workbench/my-approvals（Flowable 真任务）
const approvals = ref([])
const BIZ_LABEL = { POLICY: '制度审批', RISK_ACCEPTANCE: '风险接受', FEEDBACK_OUTBOUND: '出站审批', REG_FILING: '报送复核' }
const agoText = (ms) => {
  if (!ms) return ''
  const h = Math.floor((Date.now() - ms) / 3600000)
  return h < 1 ? '刚刚' : (h < 24 ? h + ' 小时前' : Math.floor(h / 24) + ' 天前')
}

// 实时事件流 ← /api/workbench/notifications（调度提醒/规则告警，取最新 6 条）
const feed = ref([])
const feedBadge = (et) => et && et.startsWith('RULE_KRI') ? 'kri'
  : (et && et.startsWith('RULE_REG') ? 'law' : (et && et.includes('AUDIT') ? 'aud' : 'over'))
const feedBadgeText = (et) => et && et.startsWith('RULE_KRI') ? 'KRI'
  : (et && et.startsWith('RULE_REG') ? '法规' : (et && et.includes('AUDIT') ? '审计' : '提醒'))

async function loadLiveWidgets() {
  try { kris.value = await api.get('/kris'); loadKriSparks() } catch (e) { kris.value = [] }
  try { approvals.value = await api.get('/workbench/my-approvals') } catch (e) { approvals.value = [] }
  try { feed.value = (await api.get('/workbench/notifications')).slice(0, 6) } catch (e) { feed.value = [] }
}
loadLiveWidgets()
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-dashboard 区块的内联 CSS。
   颜色一律走 tokens.css 语义令牌（热力单元格底色除外，按原型保留内联色）。
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
.phead h1 small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-3);
  margin-left: 8px;
  font-family: var(--font-sans);
}
.phead .sp {
  flex: 1;
}

/* ---- 子公司分段 seg ---- */
.seg {
  display: inline-flex;
  gap: 3px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  padding: 3px;
  box-shadow: var(--shadow-1);
}
.seg button {
  border: 0;
  background: none;
  padding: 6px 12px;
  font-size: 11.5px;
  color: var(--text-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.seg button.on {
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-weight: 600;
}

/* ---- 按钮 btn ---- */
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

/* ---- KPI 指标卡组 ---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
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
.kc .v .up {
  font-size: 11px;
  color: var(--success);
  font-weight: 700;
}
.kc .v .dn {
  font-size: 11px;
  color: var(--danger);
  font-weight: 700;
}
.kc .s {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 5px;
}
.kc .pb {
  height: 4px;
  background: rgba(120, 120, 120, 0.13);
  border-radius: 3px;
  margin-top: 9px;
  overflow: hidden;
}
.kc .pb i {
  display: block;
  height: 100%;
  border-radius: 3px;
}

/* ---- 可编辑大屏栅格（12 列 span） ---- */
.dgrid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 14px;
  margin-bottom: 14px;
  grid-auto-flow: row dense;
}
.dgrid .gi {
  grid-column: span var(--w, 6);
  min-width: 0;
  margin-bottom: 0;
  position: relative;
}
@media (max-width: 1180px) {
  .dgrid .gi {
    grid-column: span 6 !important;
  }
}
@media (max-width: 820px) {
  .dgrid .gi {
    grid-column: span 12 !important;
  }
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
  font-weight: 600;
  cursor: pointer;
}
.cb {
  padding: 14px 18px 18px;
}

/* ---- 热力矩阵 heat（等宽列） ---- */
.heat {
  width: 100%;
  border-collapse: separate;
  border-spacing: 3px;
  table-layout: fixed;
}
.heat th:first-child,
.heat td.lbl {
  width: 96px;
}
.heat th {
  font-size: 10.5px;
  color: var(--text-3);
  font-weight: 600;
  padding: 3px 0;
  text-align: center;
}
.heat td.lbl {
  font-size: 11.5px;
  color: var(--text-2);
  text-align: right;
  padding-right: 8px;
  white-space: nowrap;
}
.heat td.cell {
  text-align: center;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
  padding: 8px 0;
  color: #fff;
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
.bar-row .hd .ov {
  color: var(--danger);
  font-size: 11px;
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
.seg2.h {
  background: var(--danger);
}
.seg2.m {
  background: var(--warning);
}
.seg2.l {
  background: var(--safe);
}
.seg2.a {
  background: var(--accent);
}
.seg2.g {
  background: var(--success);
}

/* ---- KRI 监控列表 ---- */
.kri .ki {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.kri .ki:last-child {
  border: 0;
}
.kri .ki .dt {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.kri .ki .nm {
  flex: 1;
}
.kri .ki .nm .t {
  font-size: 12.5px;
}
.kri .ki .nm .src {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 1px;
}
.kri .ki .val {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.kri .ki .val .th {
  font-size: 10.5px;
  color: var(--text-3);
  font-weight: 400;
}
.kspark {
  width: 54px;
  height: 18px;
  flex-shrink: 0;
}

/* ---- 待我审批 worklist ---- */
.wl .wi {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.wl .wi:last-child {
  border: 0;
}
.wl .tp2 {
  font-size: 10px;
  font-weight: 700;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--accent-weak);
  color: var(--accent-strong);
  flex-shrink: 0;
  white-space: nowrap;
}
.wl .ti {
  flex: 1;
  min-width: 0;
}
.wl .ti .t {
  font-size: 12.5px;
}
.wl .ti .m {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 2px;
}
.wl .due {
  font-size: 11px;
  color: var(--text-3);
  white-space: nowrap;
}
.wl .due.ov {
  color: var(--danger);
  font-weight: 600;
}

/* ---- 实时事件流 feed ---- */
.feed .it {
  display: flex;
  gap: 11px;
  padding: 11px 18px;
  border-top: 1px solid var(--border-subtle);
}
.feed .it:first-child {
  border-top: 0;
}
.feed .tm {
  font-size: 10.5px;
  color: var(--text-3);
  width: 42px;
  flex-shrink: 0;
  padding-top: 2px;
  font-variant-numeric: tabular-nums;
}
.feed .bd {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  height: 18px;
  display: inline-flex;
  align-items: center;
}
.bd.law {
  background: var(--accent-weak);
  color: var(--accent-strong);
}
.bd.over {
  background: var(--danger-tint);
  color: var(--danger);
}
.bd.kri {
  background: var(--plum-tint);
  color: var(--plum);
}
.bd.aud {
  background: var(--warning-tint);
  color: #a87d22;
}
.bd.rev {
  background: var(--info-tint);
  color: var(--info);
}
.feed .tx {
  font-size: 12px;
  line-height: 1.45;
}
/* 诚实标注条：区分真实 KPI 与原型示意分析图 */
.dash-note {
  margin: 0 0 14px;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-2);
  background: var(--info-tint, rgba(40, 90, 150, 0.08));
  border: 1px solid var(--surface-border);
  border-left: 3px solid var(--info, #3a6ea5);
  border-radius: var(--radius-md);
}
/* ===== 大屏布局编辑：调整大小 / 增删组件 ===== */
.btn.ghost.on { border-color: var(--accent); color: var(--accent-strong); background: var(--accent-tint); }
/* 每组件右上角的「大小段控 + 移除」编辑条 */
.wedit { position: absolute; top: 8px; right: 10px; z-index: 6; display: inline-flex; gap: 2px; background: var(--surface); border: 1px solid var(--surface-border); border-radius: 8px; padding: 2px; box-shadow: var(--shadow-1); }
.wsz { font-size: 10.5px; font-weight: 600; padding: 3px 7px; border: 0; background: none; color: var(--text-2); border-radius: 6px; cursor: pointer; font-family: inherit; }
.wsz:hover { background: var(--accent-tint); color: var(--accent-strong); }
.wsz.on { background: var(--accent); color: #fff; }
.wsz.rm { color: var(--danger); }
.wsz.rm:hover { background: var(--danger); color: #fff; }
/* 自由缩放：右边缘拖拽手柄 */
.dgrid.editing .gi { outline: 1px dashed color-mix(in srgb, var(--accent) 55%, transparent); outline-offset: -1px; }
.wgrip { position: absolute; top: 0; right: 0; width: 12px; height: 100%; cursor: col-resize; z-index: 5; display: flex; align-items: center; justify-content: center; }
.wgrip::after { content: ''; width: 4px; height: 40px; border-radius: 3px; background: var(--accent); opacity: 0.4; transition: opacity 0.15s; }
.wgrip:hover::after { opacity: 0.9; height: 56px; }
.addwrap { position: relative; }
.abadge { margin-left: 6px; font-size: 10px; font-weight: 700; background: var(--accent); color: #fff; border-radius: 999px; padding: 0 6px; }
.addmenu { position: absolute; inset: 0 0 auto auto; z-index: 60; }
.addmenu-pop { position: absolute; top: 8px; right: 0; min-width: 248px; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-md); box-shadow: var(--shadow-2); padding: 8px; }
.amh { font-size: 11px; color: var(--text-3); padding: 4px 8px 8px; font-weight: 700; }
.ami { display: flex; align-items: center; width: 100%; text-align: left; border: 0; background: none; color: var(--text-1); font-size: 12.5px; padding: 7px 8px; border-radius: 6px; cursor: pointer; font-family: inherit; }
.ami:hover { background: var(--accent-tint); color: var(--accent-strong); }
.ami .extag { margin-left: auto; font-size: 9.5px; font-weight: 700; background: var(--accent-weak); color: var(--accent-strong); border-radius: 4px; padding: 0 5px; }
.ami.rst { color: var(--text-2); }
.amsep { height: 1px; background: var(--border-subtle); margin: 6px 4px; }
.amempty { font-size: 11.5px; color: var(--text-3); padding: 8px; line-height: 1.6; }
/* 额外可视化组件 */
.exdonut { display: flex; align-items: center; gap: 16px; padding: 8px 4px; }
.exd-c b { font-size: 22px; font-weight: 760; font-family: var(--font-display); display: block; }
.exd-c span { font-size: 11.5px; color: var(--text-3); }
.exbars { display: flex; flex-direction: column; gap: 9px; padding: 4px 2px; }
.exbar-row { display: flex; align-items: center; gap: 8px; font-size: 11.5px; }
.exbar-row .exl { width: 28px; color: var(--text-2); }
.exbar-row .extrack { flex: 1; height: 8px; background: var(--bg); border-radius: 5px; overflow: hidden; }
.exbar-row .extrack i { display: block; height: 100%; border-radius: 5px; }
.exbar-row b { width: 22px; text-align: right; font-variant-numeric: tabular-nums; }
.exbar-row .exl { width: 40px; }
.exempty { text-align: center; color: var(--text-3); font-size: 12px; padding: 14px 0; }
/* 组织 × 六域构成条 */
.orgbar-row { padding: 5px 2px; }
.orgbar-row .hd { display: flex; justify-content: space-between; font-size: 11.5px; margin-bottom: 4px; }
.orgbar-row .nm { color: var(--text-2); }
.orgbar-track { display: flex; height: 10px; border-radius: 5px; overflow: hidden; background: var(--bg); }
.orgbar-track i { display: block; height: 100%; }
.orgbar-legend { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 8px; font-size: 10.5px; color: var(--text-3); }
.orgbar-legend i { display: inline-block; width: 8px; height: 8px; border-radius: 2px; margin-right: 4px; vertical-align: middle; }
/* 排行/报送行 */
.rankrow { display: flex; align-items: center; gap: 8px; padding: 6px 2px; border-bottom: 1px solid var(--border-subtle); font-size: 11.5px; }
.rankrow:last-of-type { border-bottom: 0; }
.rankrow .nm { color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rankrow .ov { color: var(--danger); font-weight: 700; }
.rankrow .code2 { color: var(--accent-strong); font-weight: 700; }
.rankrow .fdate { margin-left: auto; color: var(--text-3); font-variant-numeric: tabular-nums; }
.rankrow .ftag { padding: 1px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 700; background: rgba(120,120,120,.1); color: var(--text-2); white-space: nowrap; }
.rankrow .ftag.warn { background: var(--warning-tint); color: #a87d22; }
.rankrow .ftag.over { background: var(--danger-tint); color: var(--danger); }
/* KRI 状态计数 */
.kricnt { display: flex; gap: 12px; padding: 10px 2px; }
.kc2 { flex: 1; text-align: center; padding: 12px 0; border-radius: var(--radius-md); background: var(--bg); }
.kc2 b { display: block; font-size: 24px; font-family: var(--font-display); }
.kc2 span { font-size: 11px; color: var(--text-3); }
.kc2.ok b { color: var(--success); } .kc2.warn b { color: #a87d22; } .kc2.crit b { color: var(--danger); }
/* KPI 下钻弹层 */
.kc.clk { cursor: pointer; }
.kc.clk:hover { border-color: var(--accent); }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 480px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 14px; font-size: 16px; }
.kd-v { font-size: 14px; margin-bottom: 12px; }
.kd-v b { font-size: 20px; font-family: var(--font-display); }
.kd-row { display: flex; gap: 10px; font-size: 12.5px; color: var(--text-2); padding: 7px 0; border-top: 1px solid var(--border-subtle); line-height: 1.6; }
.kd-k { flex-shrink: 0; width: 60px; color: var(--text-3); font-weight: 700; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
/* B37 benchmark 表 */
.bm { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.bm th { font-size: 11px; color: var(--text-3); font-weight: 700; padding: 6px 8px; text-align: center; }
.bm td { padding: 7px 8px; text-align: center; border-top: 1px solid var(--border-subtle); }
.bm-hi { color: var(--danger); font-weight: 700; }
.bm-lo { color: var(--success); font-weight: 700; }
/* #6 缩放实时长宽读数 */
.resize-badge { position: fixed; z-index: 2000; pointer-events: none; background: var(--text-1, #1a1a1a); color: #fff; font-size: 12px; padding: 5px 10px; border-radius: 7px; box-shadow: 0 6px 20px rgba(0,0,0,0.28); white-space: nowrap; }
.resize-badge b { font-variant-numeric: tabular-nums; }
</style>
