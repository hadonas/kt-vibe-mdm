'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { 
  Bars3Icon, 
  XMarkIcon,
  DocumentTextIcon,
  FolderIcon,
  ChatBubbleLeftRightIcon,
  ClipboardDocumentCheckIcon,
  HomeIcon,
  CogIcon,
  UserIcon
} from '@heroicons/react/24/outline'
import { Button } from '@/components/ui/Button'
import { useAuth } from '@/contexts/AuthContexts'

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false)
  const { isAuthenticated, user, isLoading, isApprover, logout } = useAuth()
  const router = useRouter()

  const handleLogout = () => {
    logout()
    router.replace('/login')
  }

  const navigation = [
    { name: '대시보드', href: '/dashboard', icon: HomeIcon },
    { name: '단일 문서 등록', href: '/ingest/document', icon: DocumentTextIcon },
    { name: '레포지토리 등록', href: '/ingest/repository', icon: FolderIcon },
    { name: '내 요청 관리', href: '/my-requests', icon: UserIcon },
    { name: 'RAG 채팅', href: '/chat', icon: ChatBubbleLeftRightIcon },
  ]

  if (isApprover) {
    navigation.push({ 
      name: '승인 관리', 
      href: '/approval', 
      icon: ClipboardDocumentCheckIcon 
    })
  }

  // 관리자 전용 메뉴
  if (user?.roles?.includes('ADMIN')) {
    navigation.push({ 
      name: '관리자', 
      href: '/admin', 
      icon: CogIcon 
    })
  }

  // 로딩 중일 때는 기본 네비게이션만 표시
  if (isLoading) {
    return (
      <nav className="bg-white shadow-lg">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <Link href="/dashboard" className="flex-shrink-0">
                <h1 className="text-2xl font-bold text-gray-800">MDM</h1>
              </Link>
            </div>
            <div className="hidden md:flex items-center space-x-8">
              <div className="w-20 h-8 bg-gray-200 animate-pulse rounded"></div>
            </div>
          </div>
        </div>
      </nav>
    )
  }

  return (
    <nav className="bg-white shadow-lg">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link href="/dashboard" className="flex-shrink-0">
              <h1 className="text-2xl font-bold text-gray-800">MDM</h1>
            </Link>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center space-x-8">
            {isAuthenticated && navigation.map((item) => (
              <Link
                key={item.name}
                href={item.href}
                className="text-gray-600 hover:text-gray-900 px-3 py-2 rounded-md text-sm font-medium flex items-center space-x-1"
              >
                <item.icon className="h-4 w-4" />
                <span>{item.name}</span>
              </Link>
            ))}
            
            {isAuthenticated ? (
              <div className="flex items-center space-x-4">
                <span className="text-sm text-gray-600">
                  {user?.name} ({user?.roles.join(', ')})
                </span>
                <Button variant="outline" size="sm" onClick={handleLogout}>
                  로그아웃
                </Button>
              </div>
            ) : (
              <div className="flex items-center space-x-2">
                <Link href="/login">
                  <Button variant="outline" size="sm">로그인</Button>
                </Link>
                <Link href="/signup">
                  <Button size="sm">회원가입</Button>
                </Link>
              </div>
            )}
          </div>

          {/* Mobile menu button */}
          <div className="md:hidden flex items-center">
            <button
              onClick={() => setIsOpen(!isOpen)}
              className="text-gray-600 hover:text-gray-900 focus:outline-none focus:text-gray-900"
            >
              {isOpen ? (
                <XMarkIcon className="h-6 w-6" />
              ) : (
                <Bars3Icon className="h-6 w-6" />
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Navigation */}
      {isOpen && (
        <div className="md:hidden">
          <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3 bg-gray-50">
            {isAuthenticated && navigation.map((item) => (
              <Link
                key={item.name}
                href={item.href}
                className="text-gray-600 hover:text-gray-900 block px-3 py-2 rounded-md text-base font-medium flex items-center space-x-2"
                onClick={() => setIsOpen(false)}
              >
                <item.icon className="h-5 w-5" />
                <span>{item.name}</span>
              </Link>
            ))}
            
            {isAuthenticated ? (
              <div className="border-t pt-3 mt-3">
                <div className="px-3 py-2 text-sm text-gray-600">
                  {user?.name} ({user?.roles.join(', ')})
                </div>
                <button
                  onClick={handleLogout}
                  className="w-full text-left px-3 py-2 text-gray-600 hover:text-gray-900"
                >
                  로그아웃
                </button>
              </div>
            ) : (
              <div className="border-t pt-3 mt-3 space-y-2">
                <Link href="/login" onClick={() => setIsOpen(false)}>
                  <Button variant="outline" className="w-full">로그인</Button>
                </Link>
                <Link href="/signup" onClick={() => setIsOpen(false)}>
                  <Button className="w-full">회원가입</Button>
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </nav>
  )
}
