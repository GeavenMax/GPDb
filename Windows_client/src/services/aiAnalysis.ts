import { ref } from 'vue';
import { api } from '../api';
import { loadDeepInsights, type DeepInsightsData } from './userAnalytics';

export interface AiPersonaReport {
  archetype: string;
  keywords: string[];
  summary: string;
  fullMarkdown: string;
  generatedAt: string;
  sampleFavoritesCount: number;
}

const STORAGE_KEY = 'gpdb_ai_persona_report';

export type AiAnalysisStage = 'collecting' | 'prompting' | 'analyzing' | 'finalizing' | 'success' | 'error';
export const activeAiReport = ref<AiPersonaReport | null>(loadCachedReport());
export const isAiAnalyzing = ref(false);
export const aiAnalysisError = ref<string | null>(null);
export const aiAnalysisStage = ref<AiAnalysisStage>('collecting');
export const aiAnalysisStepText = ref<string>('');

function loadCachedReport(): AiPersonaReport | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return null;
}

export function saveAiReport(report: AiPersonaReport) {
  activeAiReport.value = report;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(report));
  } catch {}
}

export function clearAiReport() {
  activeAiReport.value = null;
  localStorage.removeItem(STORAGE_KEY);
}

export async function generateAiPersonaInsight(customPromptSuffix: string = ''): Promise<AiPersonaReport> {
  isAiAnalyzing.value = true;
  aiAnalysisError.value = null;
  aiAnalysisStage.value = 'collecting';
  aiAnalysisStepText.value = '正在梳理本地媒体库收藏、标记与观影足迹...';

  try {
    const insights: DeepInsightsData = await loadDeepInsights();
    const favs = insights.userFavorites;

    // Collect titles
    const movies = [
      ...(favs?.movie || []),
      ...(favs?.watched || []),
      ...(favs?.wishlist || []),
    ];

    const uniqueMovieTitles = Array.from(
      new Set(
        movies.map(m => {
          const title = m.title || m.title_zh || m.name || '';
          const yr = m.release_year ? ` (${m.release_year})` : '';
          const stu = m.studio_name ? ` [厂牌: ${m.studio_name}]` : '';
          return title ? `${title}${yr}${stu}` : '';
        }).filter(Boolean)
      )
    ).slice(0, 30);

    const performerNames = Array.from(
      new Set((favs?.performer || []).map(p => p.name || '').filter(Boolean))
    ).slice(0, 25);

    const studioNames = Array.from(
      new Set((favs?.studio || []).map(s => s.name || '').filter(Boolean))
    ).slice(0, 15);

    const directorNames = Array.from(
      new Set((favs?.director || []).map(d => d.name || '').filter(Boolean))
    ).slice(0, 15);

    const topEras = insights.eras.filter(e => e.count > 0).map(e => `${e.era}: ${e.count}部`);
    const topStudios = insights.topStudios.slice(0, 8).map(s => `${s.studio}: ${s.count}部`);

    const hasData = uniqueMovieTitles.length > 0 || performerNames.length > 0 || studioNames.length > 0;
    if (!hasData) {
      throw new Error('您的收藏夹与观影记录尚为空白。请先在影片库或演员库中收藏/标记几部喜爱的作品，再让 AI 为您生成深度偏好洞察！');
    }

    aiAnalysisStage.value = 'prompting';
    aiAnalysisStepText.value = '正在安全构建加密提示词上下文并连接大模型...';

    const prompt = `你是一位学识渊博、洞察入微的资深电影文化学者、艺术品鉴家兼私人影视导赏专家。
以下是某位资深影迷在本地私人媒体库中累积的真实观影足迹与收藏数据：

【用户收藏与标记的代表影片 (共 ${uniqueMovieTitles.length} 部)】:
${uniqueMovieTitles.map(t => `- ${t}`).join('\n')}

【钟爱的核心演员列表 (共 ${performerNames.length} 位)】:
${performerNames.join(', ') || '暂无单列'}

【偏好厂牌 / 电影公司】:
${studioNames.join(', ') || topStudios.join(', ') || '各独立制片厂牌'}

【偏好导演】:
${directorNames.join(', ') || '多元导演探索'}

【观影年代分布与沉淀】:
${topEras.join(' | ') || '年代广泛分布'}

${customPromptSuffix ? `【用户补充特别关注点】: ${customPromptSuffix}\n` : ''}

请针对以上真实数据，为该影迷撰写一份结构严谨、文笔犀利优美、洞察精准且极具艺术鉴赏格调的《影迷专属艺术画像与偏好深度洞察报告》。
请包含以下四大核心版块（请使用清晰的 Markdown 标题输出）：

### 1. 🎬 核心影迷画像与观影流派定位
总结其艺术审美性格，赋予其一个极具文学感或学术感的专属影迷代号/流派原型（例如：“世纪末胶片复古主义鉴赏家”、“现代硬派极简叙事拥趸”等），并深度剖析其核心审美取向。

### 2. 💎 时代光谱与厂牌美学解构
从偏好的年代跨度与厂牌基因切入，剖析其对视觉质感、叙事节奏与制作风格的深层偏好。

### 3. 🌟 钟爱面孔与演员化学反应图景
解构其偏好演员的共通特质（气质、体态、张力、表演风格），并提炼其最着迷的人物互动模式或化学反应。

### 4. 🧭 专属定制寻宝与探索指南
根据已知偏好，为其指明 3~4 个兼具相似性与突破性的探索维度或可能被忽视的冷门宝藏方向。

---
最后，请务必在报告的最末尾提供一段严格合法的 JSON 代码块（用 \`\`\`json 包裹），字段格式如下：
\`\`\`json
{
  "archetype": "专属影迷代号 (不超过15字)",
  "keywords": ["关键词1", "关键词2", "关键词3", "关键词4"],
  "summary": "一句凝练到位的影迷画像总结语 (50字以内)"
}
\`\`\`
`;

    aiAnalysisStage.value = 'analyzing';
    aiAnalysisStepText.value = '大模型正在深度研判您的艺术审美偏好与流派原型...';

    const rawResponse = await api.runAiAnalysis(prompt);

    aiAnalysisStage.value = 'finalizing';
    aiAnalysisStepText.value = '正在提炼专属原型代号、标签与定制寻宝指南...';

    // Parse JSON block at the end if present
    let archetype = '深度影视鉴赏家';
    let keywords: string[] = ['经典叙事', '个性厂牌', '独到品味'];
    let summary = '兼具经典底蕴与个性化美学探索的专属影迷。';

    const jsonMatch = rawResponse.match(/```json\s*([\s\S]*?)\s*```/);
    if (jsonMatch && jsonMatch[1]) {
      try {
        const parsed = JSON.parse(jsonMatch[1]);
        if (parsed.archetype) archetype = parsed.archetype;
        if (Array.isArray(parsed.keywords)) keywords = parsed.keywords;
        if (parsed.summary) summary = parsed.summary;
      } catch (err) {
        console.warn('Failed to parse AI JSON block', err);
      }
    }

    const report: AiPersonaReport = {
      archetype,
      keywords,
      summary,
      fullMarkdown: rawResponse,
      generatedAt: new Date().toISOString(),
      sampleFavoritesCount: uniqueMovieTitles.length,
    };

    saveAiReport(report);
    aiAnalysisStage.value = 'success';
    aiAnalysisStepText.value = '专属影迷画像研判完成！';
    return report;
  } catch (err: any) {
    const msg = err?.message || String(err) || '生成画像失败';
    aiAnalysisError.value = msg;
    aiAnalysisStage.value = 'error';
    throw new Error(msg);
  } finally {
    isAiAnalyzing.value = false;
  }
}

export function exportAiReportMarkdown(report: AiPersonaReport) {
  const content = `# GPDb 影迷画像与偏好深度洞察报告
生成时间: ${new Date(report.generatedAt).toLocaleString()}
专属代号: ${report.archetype}
核心标签: ${report.keywords.join(' · ')}
画像概括: ${report.summary}

---

${report.fullMarkdown}
`;

  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `GPDb_影迷偏好洞察_${report.archetype.replace(/[\/\s]/g, '_')}.md`;
  a.click();
  URL.revokeObjectURL(url);
}
