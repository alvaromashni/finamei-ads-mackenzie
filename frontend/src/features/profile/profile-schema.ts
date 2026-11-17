import { z } from 'zod'
import { toIsoDate } from '../../lib/format'

/** Espelha as regras do RegisterUserRequest no backend (nome ≤ 120, e-mail ≤ 180). */
export const profileSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Informe o nome.')
    .max(120, 'O nome deve ter no máximo 120 caracteres.'),
  email: z
    .string()
    .trim()
    .min(1, 'Informe o e-mail.')
    .max(180, 'O e-mail deve ter no máximo 180 caracteres.')
    .pipe(z.email('Informe um e-mail válido.')),
  meiOpeningDate: z
    .string()
    .refine(
      (value) => value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value),
      'Informe uma data válida.',
    )
    .refine(
      (value) => value === '' || value <= toIsoDate(new Date()),
      'A data de abertura não pode ser futura.',
    ),
})

export type ProfileFormValues = z.infer<typeof profileSchema>

export const PROFILE_FIELDS = ['name', 'email', 'meiOpeningDate'] as const
