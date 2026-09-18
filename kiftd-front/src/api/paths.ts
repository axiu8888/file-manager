/**
 * REST 相对路径（不含 /api 前缀）。axios 的 baseURL 会自动补上。
 * 改路径只改这里；页面不要拼字符串。
 */
export const paths = {
  auth: {
    publicKey: '/auth/public-key',
    captcha: '/auth/captcha',
    signupEnabled: '/auth/signup-enabled',
    login: '/auth/login',
    signup: '/auth/signup',
    changePassword: '/auth/change-password',
  },
  folders: {
    view: '/folders/view',
    remaining: '/folders/remaining',
    search: '/folders/search',
    create: '/folders',
    one: (id: string) => `/folders/${id}`,
    count: (id: string) => `/folders/${id}/count`,
  },
  files: {
    checkUpload: '/files/check-upload',
    upload: '/files/upload',
    one: (id: string) => `/files/${id}`,
    content: (id: string) => `/files/${id}/content`,
    batchDelete: '/files/batch-delete',
    download: (id: string) => `/files/${id}/download`,
    confirmMove: '/files/confirm-move',
    move: '/files/move',
    zip: '/files/zip',
  },
  links: {
    chain: '/links/chain',
    downloadKey: '/links/download-key',
    chainPublic: (key: string) => `/links/chain/${key}`,
    downloadPublic: (key: string) => `/links/download/${key}`,
  },
  system: {
    notice: '/system/notice',
    os: '/system/os',
  },
  preview: {
    pictures: '/preview/pictures',
    audios: '/preview/audios',
    video: '/preview/video',
    videos: '/preview/videos',
    siblings: '/preview/siblings',
    excel: '/preview/excel',
    ppt: '/preview/ppt',
    transcodeStatus: '/preview/transcode-status',
    resource: (id: string) => `/preview/resource/${id}`,
    pdf: (id: string) => `/preview/pdf/${id}`,
    txtPdf: (id: string) => `/preview/txt-pdf/${id}`,
    officePdf: (id: string) => `/preview/office-pdf/${id}`,
    pptSlide: (id: string, index: number) => `/preview/ppt-slide/${id}/${index}`,
  },
} as const
