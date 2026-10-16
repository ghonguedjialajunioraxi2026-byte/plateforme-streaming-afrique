/// Service utilisateurs.
import { http } from '../http'
import type { User } from '../../types/domain'

export const usersApi = {
  getUser: (id: string) => http.get<User>(`/users/${id}`),
  updateProfile: (id: string, data: Partial<User>) =>
    http.put<User>(`/users/${id}`, data),
}