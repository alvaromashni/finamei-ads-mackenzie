import { z } from 'zod'

/** Mirrors RegisterUserRequest in the backend; the password follows RN09. */
export const registerSchema = z
  .object({
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
    password: z
      .string()
      .min(1, 'Informe a senha.')
      .regex(
        /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
        'A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número.',
      ),
    passwordConfirmation: z.string().min(1, 'Repita a senha.'),
    role: z.enum(['MEI', 'ACCOUNTANT']),
  })
  .refine((values) => values.password === values.passwordConfirmation, {
    path: ['passwordConfirmation'],
    message: 'As senhas não são iguais. Digite a mesma senha nos dois campos.',
  })

export type RegisterFormValues = z.infer<typeof registerSchema>

export const REGISTER_FIELDS = ['name', 'email', 'password'] as const
