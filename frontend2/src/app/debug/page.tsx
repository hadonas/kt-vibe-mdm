'use client'

import { useAuth } from '@/hooks/useAuth'
import AuthService from '@/lib/auth'

export default function DebugPage() {
  const { isAuthenticated, user, isLoading, isAdmin, isApprover } = useAuth()

  const handleLogin = async () => {
    try {
      await AuthService.login({
        email: 'test@test.com',
        password: 'test123456'
      })
      window.location.reload()
    } catch (error) {
      console.error('Login failed:', error)
    }
  }

  const handleLogout = () => {
    AuthService.logout()
    window.location.reload()
  }

  const checkLocalStorage = () => {
    if (typeof window !== 'undefined') {
      console.log('localStorage contents:')
      console.log('accessToken:', localStorage.getItem('accessToken'))
      console.log('refreshToken:', localStorage.getItem('refreshToken'))
      console.log('user:', localStorage.getItem('user'))
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-4xl mx-auto">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">인증 디버그 페이지</h1>
        
        <div className="grid gap-6">
          {/* 현재 상태 */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">현재 인증 상태</h2>
            <div className="space-y-2">
              <p><strong>isLoading:</strong> {isLoading ? '✅ true' : '❌ false'}</p>
              <p><strong>isAuthenticated:</strong> {isAuthenticated ? '✅ true' : '❌ false'}</p>
              <p><strong>user:</strong> {user ? `✅ ${user.name} (${user.email})` : '❌ null'}</p>
              <p><strong>isAdmin:</strong> {isAdmin ? '✅ true' : '❌ false'}</p>
              <p><strong>isApprover:</strong> {isApprover ? '✅ true' : '❌ false'}</p>
            </div>
          </div>

          {/* 액션 버튼들 */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">테스트 액션</h2>
            <div className="space-x-4">
              <button 
                onClick={handleLogin}
                className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
              >
                테스트 로그인
              </button>
              <button 
                onClick={handleLogout}
                className="bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
              >
                로그아웃
              </button>
              <button 
                onClick={checkLocalStorage}
                className="bg-gray-500 text-white px-4 py-2 rounded hover:bg-gray-600"
              >
                localStorage 확인
              </button>
            </div>
          </div>

          {/* AuthService 직접 호출 결과 */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">AuthService 직접 호출 결과</h2>
            <div className="space-y-2">
              <p><strong>AuthService.isAuthenticated():</strong> {AuthService.isAuthenticated() ? '✅ true' : '❌ false'}</p>
              <p><strong>AuthService.getCurrentUser():</strong> {JSON.stringify(AuthService.getCurrentUser(), null, 2)}</p>
            </div>
          </div>

          {/* localStorage 내용 */}
          <div className="bg-white p-6 rounded-lg shadow">
            <h2 className="text-xl font-semibold mb-4">localStorage 내용</h2>
            <pre className="bg-gray-100 p-4 rounded text-sm overflow-auto">
              {typeof window !== 'undefined' ? JSON.stringify({
                accessToken: localStorage.getItem('accessToken'),
                refreshToken: localStorage.getItem('refreshToken'),
                user: localStorage.getItem('user')
              }, null, 2) : 'Server-side rendering'}
            </pre>
          </div>
        </div>
      </div>
    </div>
  )
}
