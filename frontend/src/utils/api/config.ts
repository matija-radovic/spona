import axios from 'axios'
import { clearToken, getToken } from '@utils/authToken'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL
})

const storageOrigin: string | undefined = import.meta.env.VITE_STORAGE_ORIGIN?.replace(/\/$/, '')

const throughStorageProxy = (value: unknown): unknown => {
    if (typeof value === 'string') {
        return storageOrigin && value.startsWith(`${storageOrigin}/`)
            ? `/storage${value.slice(storageOrigin.length)}`
            : value
    }
    if (Array.isArray(value)) return value.map(throughStorageProxy)
    if (value !== null && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype) {
        return Object.fromEntries(
            Object.entries(value).map(([key, inner]) => [key, throughStorageProxy(inner)]),
        )
    }
    return value
}

api.interceptors.request.use((config) => {
    const token = getToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
})

api.interceptors.response.use(
    (response) => {
        if (storageOrigin) response.data = throughStorageProxy(response.data)
        return response
    },
    (error) => {
        if (error?.response?.status === 401) {
            clearToken()
            if (window.location.pathname !== '/login') {
                window.location.assign('/login')
            }
        }
        return Promise.reject(error)
    },
)

export default api
