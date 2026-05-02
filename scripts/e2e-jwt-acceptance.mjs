import fs from 'node:fs/promises';

const BASE_URL = 'http://127.0.0.1:8081';
const REPORT_PATH = '/Users/houzp/Developer/projects/learning/ai-agent/tmp/e2e-jwt-acceptance-report.html';
const ADMIN_TOKEN = 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlMmUtYWRtaW4iLCJyb2xlIjoiQURNSU4iLCJyZWdpb24iOiJjaXR5IiwiaXNzIjoiaHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZS50ZXN0IiwiaWF0IjoxNzc3NzI0MDA4LCJleHAiOjE3Nzc3Mjc2MDh9.P0A25O9uv9yIOOcAjUW_T2_KdsfJ4A0VZxcyztiKlMgRjeFVE-cgGjgQRk3HFvEPga9T9QZWC8XeLqJ_SSwrai9-jQULDgg2-OnFS4SC6j9e4-ynFcwRGsE9jtjf6Xbv1RoSBJxOAgyh82HSgqF2gLMIZFyJTuZKgHMAifx6EDdo9FCQ8UyvZhDmivvjXWnyV2dLNtVi_vyNzDpjIrrYpqELqyFQoARXYu_MXeoRIB4HYpc4ERvttue89wDj4sRcs9dz4ZH_LTtv0nIg6OdkyZYtLPp4Pe8-1H_Wc85Zy6OXG7n__g-0luDuSeeMQA_EYKv_kiM1bpYcS6eUY-_2Iw';
const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY ?? '';

function now() {
  return new Date().toLocaleString('zh-CN', { hour12: false });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function summarize(value, limit = 240) {
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return text.length > limit ? `${text.slice(0, limit)}...` : text;
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers ?? {});
  headers.set('Authorization', `Bearer ${ADMIN_TOKEN}`);
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? (options.json !== undefined || options.body ? 'POST' : 'GET'),
    headers,
    body: options.body ?? (options.json !== undefined ? JSON.stringify(options.json) : undefined),
  });
  const contentType = response.headers.get('content-type') ?? '';
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text();
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${summarize(payload)}`);
  }
  if (typeof payload === 'object' && payload !== null && 'code' in payload && payload.code !== 0) {
    throw new Error(`API ${payload.code}: ${payload.message ?? 'unknown error'}`);
  }
  return typeof payload === 'object' && payload !== null && 'data' in payload ? payload.data : payload;
}

async function deepSeekSmoke() {
  if (!DEEPSEEK_API_KEY) {
    return {
      status: 'skipped',
      title: 'DeepSeek 连通性',
      detail: '未提供环境变量 DEEPSEEK_API_KEY，已跳过真实模型烟测。',
    };
  }
  const response = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${DEEPSEEK_API_KEY}`,
    },
    body: JSON.stringify({
      model: 'deepseek-v4-flash',
      messages: [{ role: 'user', content: '请只回复：ok' }],
      max_tokens: 16,
      stream: false,
    }),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`DeepSeek HTTP ${response.status}: ${summarize(payload)}`);
  }
  return {
    status: 'passed',
    title: 'DeepSeek 连通性',
    detail: `模型 ${payload.model} 返回：${payload.choices?.[0]?.message?.content ?? ''}`,
    usage: payload.usage,
  };
}

async function runAcceptance() {
  const sections = [];
  let uploadedDocumentId = null;

  try {
    sections.push({
      status: 'passed',
      title: '基础健康检查',
      detail: summarize(await request('/actuator/health', { headers: {} })),
    });

    try {
      sections.push(await deepSeekSmoke());
    } catch (error) {
      sections.push({
        status: 'failed',
        title: 'DeepSeek 连通性',
        detail: error instanceof Error ? error.message : String(error),
      });
    }

    const catalogSync = await request('/api/v1/data/catalog/sync', { json: {} });
    sections.push({
      status: 'passed',
      title: '问数目录同步',
      detail: summarize(catalogSync),
    });

    const session = await request('/api/v1/agent/sessions', {
      json: { title: `JWT 验收会话 ${Date.now()}` },
    });
    const chatMessage = await request(`/api/v1/agent/sessions/${session.id}/messages`, {
      json: { content: '请根据法规说明本周柯桥区投诉数量排行，并给出处置建议' },
    });
    const runId = chatMessage.runId;
    const plan = runId ? await request(`/api/v1/agent/sessions/runs/${runId}/plan`) : null;
    sections.push({
      status: 'passed',
      title: '聊天主链',
      detail: summarize({
        sessionId: session.id,
        runId,
        answer: chatMessage.content,
        citations: chatMessage.citations?.map((item) => item.documentTitle),
        planStatus: plan?.status,
        stepCount: plan?.steps?.length,
      }),
    });

    const queryAnswer = await request('/api/v1/data/query/answer', {
      json: { question: '请统计柯桥区当前油烟浓度超标预警数量' },
    });
    const queryRecords = await request('/api/v1/data/query/records', {
      json: {
        recordType: 'MERCHANT',
        keyword: '餐饮',
        regionCode: 'shaoxing-keqiao',
        limit: 3,
      },
    });
    sections.push({
      status: 'passed',
      title: '问数主链',
      detail: summarize({
        answer: queryAnswer.answer,
        queryId: queryAnswer.queryId,
        recordCount: queryRecords.rows?.length,
        maskedFields: queryRecords.maskedFields,
        firstRow: queryRecords.rows?.[0],
      }),
    });

  const documentBody = `# JWT 验收知识文档

柯桥区夜间巡查要求：
1. 夜间高值油烟预警商户应在 2 小时内安排复核。
2. 对连续 3 次超标商户应纳入重点巡查名单。
3. 复核结论应同步到业务系统并形成闭环台账。`;
  const uploadForm = new FormData();
  uploadForm.set('title', `JWT 验收知识文档 ${Date.now()}`);
  uploadForm.set('category', 'BUSINESS');
  uploadForm.set('sourceOrg', '柯桥区综合行政执法局');
  uploadForm.set('documentNumber', `E2E-${Date.now()}`);
  uploadForm.set('securityLevel', 'PUBLIC');
  uploadForm.set('effectiveFrom', '2026-01-01');
  uploadForm.set('regionCode', 'shaoxing-keqiao');
  uploadForm.set('summary', '用于 JWT 端到端验收的临时知识文档');
  uploadForm.set('sourceUrl', 'https://example.test/e2e-knowledge');
  uploadForm.set('file', new Blob([documentBody], { type: 'text/markdown' }), 'jwt-e2e.md');
  const uploadedDocument = await request('/api/v1/knowledge/documents', { body: uploadForm, method: 'POST' });
  uploadedDocumentId = uploadedDocument.id;
  const indexedDocument = await request(`/api/v1/knowledge/documents/${uploadedDocument.id}/index`, {
    json: {},
  });

  const attachmentForm = new FormData();
  attachmentForm.set('file', new Blob(['验收附件正文'], { type: 'text/plain' }), 'jwt-e2e-attachment.txt');
  const attachment = await request(`/api/v1/knowledge/documents/${uploadedDocument.id}/attachment`, {
    body: attachmentForm,
    method: 'POST',
  });
  const attachmentResponse = await fetch(`${BASE_URL}/api/v1/knowledge/documents/${uploadedDocument.id}/attachment`, {
    headers: { Authorization: `Bearer ${ADMIN_TOKEN}` },
  });
  const attachmentText = await attachmentResponse.text();
  if (!attachmentResponse.ok) {
    throw new Error(`附件下载失败: HTTP ${attachmentResponse.status} ${attachmentText}`);
  }
  const knowledgeSearch = await request(
    `/api/v1/knowledge/search?query=${encodeURIComponent('夜间高值油烟预警商户巡查要求')}&category=BUSINESS&limit=5`,
    { method: 'GET' },
  );
  sections.push({
    status: 'passed',
    title: '知识库主链',
    detail: summarize({
      documentId: uploadedDocument.id,
      status: indexedDocument.status,
      attachmentId: attachment.id,
      attachmentContent: attachmentText,
      searchHitCount: knowledgeSearch.length,
      firstHit: knowledgeSearch[0]?.documentTitle,
    }),
  });

  const agentRuns = await request('/api/v1/audit/agent-runs', { method: 'GET' });
  const toolCalls = await request('/api/v1/audit/tool-calls', { method: 'GET' });
  const dataAccess = await request('/api/v1/audit/data-access', { method: 'GET' });
  const modelCalls = await request('/api/v1/audit/model-calls', { method: 'GET' });
  const logs = runId
    ? await request(`/api/v1/audit/logs?runId=${encodeURIComponent(runId)}&limit=20`, { method: 'GET' })
    : [];
  sections.push({
    status: 'passed',
    title: '审计主链',
    detail: summarize({
      runAuditCount: agentRuns.length,
      toolCallCount: toolCalls.length,
      dataAccessCount: dataAccess.length,
      modelCallCount: modelCalls.length,
      runLogCount: logs.length,
      latestRun: agentRuns[0],
    }),
  });

  const failed = sections.some((item) => item.status === 'failed');
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>JWT 端到端验收报告</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f5f7fb;
      --panel: #ffffff;
      --text: #1f2937;
      --muted: #6b7280;
      --ok: #0f766e;
      --fail: #b42318;
      --skip: #9a6700;
      --border: #dbe2ea;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      background: var(--bg);
      color: var(--text);
    }
    main {
      max-width: 1080px;
      margin: 0 auto;
      padding: 32px 20px 48px;
    }
    h1 { margin: 0 0 8px; font-size: 28px; }
    p.meta { margin: 0 0 24px; color: var(--muted); }
    .summary {
      padding: 16px 18px;
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 8px;
      margin-bottom: 20px;
    }
    .status {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 13px;
    }
    .status.passed { color: var(--ok); background: #ecfdf3; }
    .status.failed { color: var(--fail); background: #fef3f2; }
    .status.skipped { color: var(--skip); background: #fffaeb; }
    .grid {
      display: grid;
      gap: 16px;
    }
    .card {
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 18px;
    }
    .card h2 {
      margin: 0 0 10px;
      font-size: 18px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    pre {
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 13px;
      line-height: 1.6;
      color: var(--text);
      background: #f8fafc;
      border-radius: 6px;
      padding: 12px;
      border: 1px solid #e5e7eb;
    }
  </style>
</head>
<body>
  <main>
    <h1>JWT 端到端验收报告</h1>
    <p class="meta">生成时间：${escapeHtml(now())} ｜ 后端：${escapeHtml(BASE_URL)} ｜ 结果：${failed ? '存在失败项' : '全部通过'}</p>
    <section class="summary">
      <span class="status ${failed ? 'failed' : 'passed'}">${failed ? '有失败项' : '全部通过'}</span>
    </section>
    <section class="grid">
      ${sections
        .map(
          (item) => `<article class="card">
            <h2>
              <span>${escapeHtml(item.title)}</span>
              <span class="status ${escapeHtml(item.status)}">${escapeHtml(item.status)}</span>
            </h2>
            <pre>${escapeHtml(item.detail)}</pre>
          </article>`,
        )
        .join('\n')}
    </section>
  </main>
</body>
</html>`;

  await fs.mkdir('/Users/houzp/Developer/projects/learning/ai-agent/tmp', { recursive: true });
  await fs.writeFile(REPORT_PATH, html, 'utf8');

    return {
      failed,
      reportPath: REPORT_PATH,
      sections,
    };
  } finally {
    if (uploadedDocumentId) {
      try {
        await request(`/api/v1/knowledge/documents/${uploadedDocumentId}/status`, {
          json: { status: 'ABOLISHED' },
        });
      } catch (error) {
        console.warn('cleanup knowledge document failed:', error);
      }
    }
  }
}

runAcceptance()
  .then((result) => {
    console.log(JSON.stringify(result, null, 2));
    process.exit(result.failed ? 1 : 0);
  })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
