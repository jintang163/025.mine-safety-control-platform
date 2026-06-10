<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  fetchReportTemplates,
  generateReport,
  fetchReportRecords,
  sendReportEmail
} from '@/utils/api'

const formatOptions = [
  { label: 'PDF', value: 'PDF' },
  { label: 'Excel', value: 'EXCEL' }
]

const templates = ref<any[]>([])
const records = ref<any[]>([])
const loadingTemplates = ref(false)
const loadingRecords = ref(false)
const generating = ref(false)
const emailVisible = ref(false)
const emailRecipients = ref('')
const currentReportId = ref<number | null>(null)
const emailSending = ref(false)

const form = ref({
  templateCode: '',
  startDate: getDefaultStart(),
  endDate: getDefaultEnd(),
  zoneCode: '',
  fileFormat: 'PDF'
})

function getDefaultStart() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return formatDate(d)
}
function getDefaultEnd() {
  return formatDate(new Date())
}
function formatDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    const res: any = await fetchReportTemplates()
    templates.value = res?.data ?? []
    if (templates.value.length && !form.value.templateCode) {
      form.value.templateCode = templates.value[0].templateCode
    }
  } catch (e) {
    templates.value = []
  } finally {
    loadingTemplates.value = false
  }
}

async function loadRecords() {
  loadingRecords.value = true
  try {
    const res: any = await fetchReportRecords()
    records.value = res?.data ?? []
  } catch (e) {
    records.value = []
  } finally {
    loadingRecords.value = false
  }
}

async function handleGenerate() {
  if (!form.value.templateCode) return
  generating.value = true
  try {
    const res: any = await generateReport({
      templateCode: form.value.templateCode,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      zoneCode: form.value.zoneCode || undefined,
      fileFormat: form.value.fileFormat,
      generatedBy: 'FRONTEND'
    })
    alert('报表生成成功！编号：' + (res?.data?.reportNo ?? ''))
    loadRecords()
  } catch (e: any) {
    alert('报表生成失败：' + (e.message ?? '未知错误'))
  } finally {
    generating.value = false
  }
}

function openEmailDialog(id: number) {
  currentReportId.value = id
  emailRecipients.value = ''
  emailVisible.value = true
}

async function handleSendEmail() {
  if (!currentReportId.value || !emailRecipients.value.trim()) {
    alert('请填写收件人')
    return
  }
  emailSending.value = true
  try {
    await sendReportEmail(currentReportId.value, emailRecipients.value.trim())
    alert('邮件发送成功')
    emailVisible.value = false
    loadRecords()
  } catch (e: any) {
    alert('邮件发送失败：' + (e.message ?? '未知错误'))
  } finally {
    emailSending.value = false
  }
}

function getStatusText(status: number) {
  return { 0: '生成中', 1: '已完成', 2: '失败' }[status] ?? '未知'
}
function getStatusClass(status: number) {
  return { 0: 'status-progress', 1: 'status-success', 2: 'status-fail' }[status] ?? ''
}

onMounted(() => {
  loadTemplates()
  loadRecords()
})
</script>

<template>
  <div class="page-container">
    <div class="page-title">
      <h2>报表管理</h2>
      <span class="sub">合规报表生成、PDF/Excel 导出与邮件推送</span>
    </div>

    <div class="card">
      <div class="card-title">生成报表</div>
      <div class="form-grid">
        <div class="form-item">
          <label>报表模板</label>
          <select v-model="form.templateCode" :disabled="loadingTemplates">
            <option v-for="t in templates" :key="t.templateCode" :value="t.templateCode">
              {{ t.templateName }} ({{ t.templateType }})
            </option>
          </select>
        </div>
        <div class="form-item">
          <label>开始日期</label>
          <input v-model="form.startDate" type="date" />
        </div>
        <div class="form-item">
          <label>结束日期</label>
          <input v-model="form.endDate" type="date" />
        </div>
        <div class="form-item">
          <label>区域编码(可选)</label>
          <input v-model="form.zoneCode" type="text" placeholder="如 ZONE-01" />
        </div>
        <div class="form-item">
          <label>导出格式</label>
          <select v-model="form.fileFormat">
            <option v-for="o in formatOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </div>
        <div class="form-item">
          <label>&nbsp;</label>
          <button class="btn btn-primary" @click="handleGenerate" :disabled="generating">
            {{ generating ? '生成中...' : '生成报表' }}
          </button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">
        <span>报表记录</span>
        <button class="btn btn-link" @click="loadRecords">刷新</button>
      </div>
      <div v-if="loadingRecords" class="loading">加载中...</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>报表编号</th>
              <th>报表名称</th>
              <th>类型</th>
              <th>统计周期</th>
              <th>格式</th>
              <th>生成方式</th>
              <th>状态</th>
              <th>生成时间</th>
              <th>邮件推送</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="r.id">
              <td class="mono">{{ r.reportNo }}</td>
              <td>{{ r.reportName }}</td>
              <td>{{ r.reportType }}</td>
              <td>{{ r.startDate }} ~ {{ r.endDate }}</td>
              <td>{{ r.fileFormat }}</td>
              <td>{{ r.generationSource === 'SCHEDULED' ? '定时' : '手动' }}</td>
              <td><span :class="['status-tag', getStatusClass(r.status)]">{{ getStatusText(r.status) }}</span></td>
              <td>{{ r.createdAt }}</td>
              <td>{{ r.emailSent ? '已推送 ' + r.emailSentTime : '未推送' }}</td>
              <td>
                <a v-if="r.fileUrl" :href="r.fileUrl" target="_blank" class="link">下载</a>
                <button v-if="r.status === 1" class="btn btn-link" @click="openEmailDialog(r.id)">邮件推送</button>
              </td>
            </tr>
            <tr v-if="records.length === 0">
              <td colspan="10" class="empty">暂无报表记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="emailVisible" class="modal-mask" @click.self="emailVisible = false">
      <div class="modal">
        <div class="modal-header">
          <h3>推送报表邮件</h3>
          <button class="close" @click="emailVisible = false">&times;</button>
        </div>
        <div class="modal-body">
          <label>收件人（多个用英文逗号分隔）</label>
          <input v-model="emailRecipients" type="text"
                 placeholder="例如：admin@mine.com, safety@mine.com"
                 class="full-width" />
        </div>
        <div class="modal-footer">
          <button class="btn" @click="emailVisible = false">取消</button>
          <button class="btn btn-primary" @click="handleSendEmail" :disabled="emailSending">
            {{ emailSending ? '发送中...' : '发送邮件' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; background: #f3f4f6; min-height: 100vh; }
.page-title { margin-bottom: 16px; }
.page-title h2 { margin: 0; color: #111827; font-size: 22px; }
.page-title .sub { color: #6b7280; font-size: 13px; margin-left: 12px; }

.card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.form-item { display: flex; flex-direction: column; }
.form-item label { font-size: 13px; color: #374151; margin-bottom: 4px; }
.form-item input, .form-item select {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
}

.btn {
  padding: 7px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  background: #e5e7eb;
  color: #1f2937;
}
.btn-primary { background: #1d4ed8; color: #fff; }
.btn-primary:disabled { background: #9ca3af; cursor: not-allowed; }
.btn-link {
  background: transparent;
  color: #1d4ed8;
  padding: 4px 8px;
}
.link { color: #1d4ed8; margin-right: 10px; cursor: pointer; text-decoration: none; }

.loading { padding: 20px; text-align: center; color: #6b7280; }
.empty { padding: 30px; text-align: center; color: #6b7280; }

.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.data-table th { background: #f9fafb; color: #374151; font-weight: 600; }
.data-table .mono { font-family: monospace; color: #1d4ed8; }

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.status-success { background: #dcfce7; color: #166534; }
.status-progress { background: #fef3c7; color: #92400e; }
.status-fail { background: #fee2e2; color: #991b1b; }

.modal-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.modal {
  background: #fff; border-radius: 8px; width: 480px; max-width: 90%;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px; border-bottom: 1px solid #e5e7eb;
}
.modal-header h3 { margin: 0; font-size: 16px; }
.modal-header .close { background: none; border: none; font-size: 22px; cursor: pointer; color: #6b7280; }
.modal-body { padding: 18px; }
.modal-body label { font-size: 13px; color: #374151; margin-bottom: 6px; display: block; }
.modal-body .full-width {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  box-sizing: border-box;
}
.modal-footer {
  padding: 12px 18px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 900px) {
  .form-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
