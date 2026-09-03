export const ContentEditorMode = Object.freeze({
  CREATE: 'create',
  EDIT: 'edit',
} as const)

export type ContentEditorMode = (typeof ContentEditorMode)[keyof typeof ContentEditorMode]
