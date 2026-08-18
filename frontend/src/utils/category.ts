// 分类标签颜色映射:按分类名自动匹配视觉分组(自由输入 + 前端正常分类)
export type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

export function categoryTagType(category?: string): TagType {
  if (!category) return 'info'
  const c = category
  if (c.includes('面试') || c.includes('工作') || c.includes('求职')) return 'warning'
  if (c.includes('学习') || c.includes('笔记') || c.includes('读书')) return 'success'
  if (
    c.includes('Java') || c.includes('技术') || c.includes('编程') ||
    c.includes('后端') || c.includes('前端') || c.includes('算法')
  ) return 'primary'
  return 'info'
}

// 常用分类提示(新建知识时 placeholder 展示)
export const COMMON_CATEGORIES = ['面试', '学习', '工作', '读书笔记', 'Java', '算法']
