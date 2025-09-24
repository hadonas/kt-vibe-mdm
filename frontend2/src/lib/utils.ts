import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDate(date: string | Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(date))
}

export function formatFileSize(bytes: number) {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export function formatCategory(category: any): string {
  if (!category) return '분류 없음'

  // displayPath 우선
  if (category.displayPath) return category.displayPath

  // hierarchy 사용
  if (Array.isArray(category.hierarchy) && category.hierarchy.length > 0) {
    return category.hierarchy
      .sort((a: any, b: any) => a.level - b.level)
      .map((l: any) => l.name)
      .join(' > ')
  }

  // names 배열 사용
  if (Array.isArray(category.names) && category.names.length > 0) {
    return category.names.join(' > ')
  }

  // 레거시 3단계 필드 사용
  const parts: string[] = []
  if (category.majorName) parts.push(category.majorName)
  if (category.midName && category.midName !== category.majorName) parts.push(category.midName)
  if (category.subName && category.subName !== category.midName) parts.push(category.subName)
  return parts.length ? parts.join(' > ') : '분류 없음'
}
