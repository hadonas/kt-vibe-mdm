'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { 
  PaperAirplaneIcon, 
  DocumentTextIcon,
  UserIcon,
  ChatBubbleLeftRightIcon
} from '@heroicons/react/24/outline'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { useAuth } from '@/hooks/useAuth'
import api from '@/lib/api'
import { ChatQueryRequest, ChatQueryResponse } from '@/types/api'
import { formatDate } from '@/lib/utils'
import { mockApi } from '@/lib/mock-api'
import { isApiImplemented } from '@/lib/api-status'

interface ChatMessage {
  id: string
  type: 'user' | 'assistant'
  content: string
  sources?: Array<{
    docId: string
    serial: string
    snippet: string
  }>
  timestamp: Date
}

export default function ChatPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const { isAuthenticated, isLoading: authLoading } = useAuth()
  const router = useRouter()

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.push('/login')
      return
    }

    if (!isAuthenticated) return
    
    // 환영 메시지 추가 (한 번만)
    if (messages.length === 0) {
      setMessages([{
        id: 'welcome',
        type: 'assistant',
        content: '안녕하세요! MDM RAG 채팅 시스템입니다.\n\n등록된 문서들에 대해 질문해주세요. 관련 문서를 검색하여 정확한 답변을 드리겠습니다.\n\n예시 질문:\n• "프로젝트 계획서에 대해 알려주세요"\n• "React 관련 문서가 있나요?"\n• "최근 등록된 API 문서를 찾아주세요"',
        timestamp: new Date()
      }])
    }
  }, [isAuthenticated, authLoading, router])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  if (authLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-blue-600"></div>
      </div>
    )
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!inputValue.trim() || isLoading) {
      return
    }

    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      type: 'user',
      content: inputValue.trim(),
      timestamp: new Date()
    }

    setMessages(prev => [...prev, userMessage])
    setInputValue('')
    setIsLoading(true)
    setError('')

    try {
      if (isApiImplemented('/chat/query')) {
        const request: ChatQueryRequest = {
          query: userMessage.content,
          context: messages
            .filter(msg => msg.type === 'user')
            .slice(-3) // 최근 3개 사용자 메시지만 컨텍스트로 사용
            .map(msg => msg.content)
        }

        const response = await api.post<ChatQueryResponse>('/chat/query', request)
        
        const assistantMessage: ChatMessage = {
          id: `assistant-${Date.now()}`,
          type: 'assistant',
          content: response.data.answer,
          sources: response.data.sources,
          timestamp: new Date()
        }

        setMessages(prev => [...prev, assistantMessage])
      } else {
        // Mock API 사용
        console.warn('Using mock API for /chat/query - backend not implemented yet')
        const mockResponse = await mockApi.chatQuery(userMessage.content)
        
        const assistantMessage: ChatMessage = {
          id: `assistant-${Date.now()}`,
          type: 'assistant',
          content: mockResponse.answer,
          sources: mockResponse.sources,
          timestamp: new Date()
        }

        setMessages(prev => [...prev, assistantMessage])
      }

    } catch (error: unknown) {
      console.error('Chat query error:', error)
      
      // Mock API로 폴백
      try {
        console.warn('Falling back to mock chat API')
        const mockResponse = await mockApi.chatQuery(userMessage.content)
        
        const assistantMessage: ChatMessage = {
          id: `assistant-${Date.now()}`,
          type: 'assistant',
          content: mockResponse.answer,
          sources: mockResponse.sources,
          timestamp: new Date()
        }

        setMessages(prev => [...prev, assistantMessage])
        setError('백엔드 API가 구현되지 않아 Mock 응답을 표시합니다.')
      } catch (mockError) {
        const errorMessage: ChatMessage = {
          id: `error-${Date.now()}`,
          type: 'assistant',
          content: '죄송합니다. 현재 서비스에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.',
          timestamp: new Date()
        }
        
        setMessages(prev => [...prev, errorMessage])
        setError('죄송합니다. 답변을 생성하는데 실패했습니다.')
      }
    } finally {
      setIsLoading(false)
      inputRef.current?.focus()
    }
  }

  const clearChat = () => {
    setMessages([{
      id: 'welcome',
      type: 'assistant',
      content: '채팅이 초기화되었습니다. 새로운 질문을 해주세요!',
      timestamp: new Date()
    }])
  }

  return (
    <div className="max-w-4xl mx-auto h-screen flex flex-col">
      {/* 헤더 */}
      <div className="bg-white border-b p-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center">
              <ChatBubbleLeftRightIcon className="h-7 w-7 mr-2 text-blue-600" />
              RAG 채팅
            </h1>
            <p className="text-gray-600 text-sm">
              등록된 문서들을 기반으로 질문에 답변해드립니다.
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={clearChat}
          >
            채팅 초기화
          </Button>
        </div>
      </div>

      {/* 채팅 메시지 영역 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex ${message.type === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-3xl ${
                message.type === 'user'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-900 border'
              } rounded-lg p-4 shadow-sm`}
            >
              <div className="flex items-start space-x-2">
                <div className={`flex-shrink-0 ${
                  message.type === 'user' ? 'text-blue-100' : 'text-gray-400'
                }`}>
                  {message.type === 'user' ? (
                    <UserIcon className="h-5 w-5" />
                  ) : (
                    <ChatBubbleLeftRightIcon className="h-5 w-5" />
                  )}
                </div>
                <div className="flex-1">
                  <div className="whitespace-pre-wrap text-sm">
                    {message.content}
                  </div>
                  
                  {/* 소스 문서들 */}
                  {message.sources && message.sources.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-gray-200">
                      <h4 className="text-xs font-medium text-gray-600 mb-2">
                        참고 문서:
                      </h4>
                      <div className="space-y-2">
                        {message.sources.map((source, index) => (
                          <div
                            key={index}
                            className="bg-gray-50 p-2 rounded text-xs"
                          >
                            <div className="flex items-center space-x-1 mb-1">
                              <DocumentTextIcon className="h-3 w-3 text-gray-500" />
                              <span className="font-medium text-gray-700">
                                {source.serial}
                              </span>
                            </div>
                            <p className="text-gray-600 line-clamp-2">
                              {source.snippet}
                            </p>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  
                  <div className={`text-xs mt-2 ${
                    message.type === 'user' ? 'text-blue-100' : 'text-gray-500'
                  }`}>
                    {formatDate(message.timestamp)}
                  </div>
                </div>
              </div>
            </div>
          </div>
        ))}

        {/* 로딩 인디케이터 */}
        {isLoading && (
          <div className="flex justify-start">
            <div className="bg-white border rounded-lg p-4 shadow-sm">
              <div className="flex items-center space-x-2">
                <ChatBubbleLeftRightIcon className="h-5 w-5 text-gray-400" />
                <div className="flex space-x-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }}></div>
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                </div>
                <span className="text-sm text-gray-500">답변을 생성하고 있습니다...</span>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 입력 영역 */}
      <div className="bg-white border-t p-4">
        {error && (
          <div className="mb-3 bg-red-50 border border-red-200 text-red-600 px-3 py-2 rounded-md text-sm">
            {error}
          </div>
        )}
        
        <form onSubmit={handleSubmit} className="flex space-x-2">
          <Input
            ref={inputRef}
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="질문을 입력하세요..."
            disabled={isLoading}
            className="flex-1"
            autoFocus
          />
          <Button
            type="submit"
            disabled={!inputValue.trim() || isLoading}
            size="icon"
          >
            <PaperAirplaneIcon className="h-4 w-4" />
          </Button>
        </form>
        
        <div className="mt-2 text-xs text-gray-500 text-center">
          등록된 문서들을 기반으로 답변이 생성됩니다. 정확한 정보를 위해 구체적으로 질문해주세요.
        </div>
      </div>

      {/* 사용 팁 */}
      {messages.length <= 1 && (
        <Card className="m-4 mt-0">
          <CardHeader>
            <CardTitle className="text-sm">💡 사용 팁</CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-gray-600 space-y-2">
            <p>• 구체적인 키워드를 포함하여 질문하세요</p>
            <p>• &quot;~에 대해 알려주세요&quot;, &quot;~가 포함된 문서를 찾아주세요&quot; 등으로 질문하세요</p>
            <p>• 문서 제목, 프로젝트명, 기술 스택 등을 언급하면 더 정확한 답변을 받을 수 있습니다</p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
