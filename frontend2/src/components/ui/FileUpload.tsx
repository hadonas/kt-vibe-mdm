'use client'

import { useRef, useState } from 'react'
import { CloudArrowUpIcon, XMarkIcon } from '@heroicons/react/24/outline'
import { Button } from './Button'
import { formatFileSize } from '@/lib/utils'

interface FileUploadProps {
  onFilesChange: (files: File[]) => void
  accept?: string
  multiple?: boolean
  maxSize?: number
  className?: string
}

export default function FileUpload({
  onFilesChange,
  accept = '.docx,.xlsx,.pdf',
  multiple = false,
  maxSize = 10 * 1024 * 1024, // 10MB
  className = ''
}: FileUploadProps) {
  const [files, setFiles] = useState<File[]>([])
  const [dragActive, setDragActive] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const validateFile = (file: File): string | null => {
    if (file.size > maxSize) {
      return `파일 크기가 너무 큽니다. 최대 ${formatFileSize(maxSize)}까지 업로드 가능합니다.`
    }
    
    if (accept) {
      const allowedExtensions = accept.split(',').map(ext => ext.trim())
      const fileExtension = '.' + file.name.split('.').pop()?.toLowerCase()
      if (!allowedExtensions.includes(fileExtension)) {
        return `지원하지 않는 파일 형식입니다. (${allowedExtensions.join(', ')}만 가능)`
      }
    }
    
    return null
  }

  const handleFiles = (newFiles: FileList) => {
    setError('')
    const fileArray = Array.from(newFiles)
    
    // 파일 검증
    for (const file of fileArray) {
      const validationError = validateFile(file)
      if (validationError) {
        setError(validationError)
        return
      }
    }

    const updatedFiles = multiple ? [...files, ...fileArray] : fileArray
    setFiles(updatedFiles)
    onFilesChange(updatedFiles)
  }

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true)
    } else if (e.type === 'dragleave') {
      setDragActive(false)
    }
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragActive(false)
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFiles(e.dataTransfer.files)
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault()
    if (e.target.files && e.target.files[0]) {
      handleFiles(e.target.files)
    }
  }

  const removeFile = (index: number) => {
    const updatedFiles = files.filter((_, i) => i !== index)
    setFiles(updatedFiles)
    onFilesChange(updatedFiles)
  }

  const openFileDialog = () => {
    fileInputRef.current?.click()
  }

  return (
    <div className={className}>
      <div
        className={`relative border-2 border-dashed rounded-lg p-6 transition-colors ${
          dragActive
            ? 'border-blue-400 bg-blue-50'
            : 'border-gray-300 hover:border-gray-400'
        }`}
        onDragEnter={handleDrag}
        onDragLeave={handleDrag}
        onDragOver={handleDrag}
        onDrop={handleDrop}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept={accept}
          multiple={multiple}
          onChange={handleChange}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        />
        
        <div className="text-center">
          <CloudArrowUpIcon className="mx-auto h-12 w-12 text-gray-400" />
          <div className="mt-4">
            <Button
              type="button"
              variant="outline"
              onClick={openFileDialog}
            >
              파일 선택
            </Button>
            <p className="mt-2 text-sm text-gray-600">
              또는 파일을 여기로 드래그하세요
            </p>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            {accept.replace(/\./g, '').toUpperCase()} 파일, 최대 {formatFileSize(maxSize)}
          </p>
        </div>
      </div>

      {error && (
        <div className="mt-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded p-2">
          {error}
        </div>
      )}

      {files.length > 0 && (
        <div className="mt-4 space-y-2">
          <h4 className="text-sm font-medium text-gray-900">선택된 파일:</h4>
          {files.map((file, index) => (
            <div
              key={index}
              className="flex items-center justify-between p-2 bg-gray-50 rounded border"
            >
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">
                  {file.name}
                </p>
                <p className="text-xs text-gray-500">
                  {formatFileSize(file.size)}
                </p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => removeFile(index)}
                className="ml-2 text-red-600 hover:text-red-800"
              >
                <XMarkIcon className="h-4 w-4" />
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
