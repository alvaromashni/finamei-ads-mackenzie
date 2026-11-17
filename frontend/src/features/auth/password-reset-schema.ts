import { z } from 'zod'

export const requestResetSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Informe o e-mail.')
    .max(180, 'O e-mail deve ter no máximo 180 caracteres.')
    .pipe(z.email('Informe um e-mail válido.')),
})

export type RequestResetValues = z.infer<typeof requestResetSchema>

/** Mesma política do cadastro. A confirmação divergente usa a frase do UC-002. */
export const confirmResetSchema = z
  .object({
    password: z
      .string()
      .min(1, 'Informe a senha.')
      .regex(
        /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
        'A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número.',
      ),
    passwordConfirmation: z.string().min(1, 'Repita a senha.'),
  })
  .refine((values) => values.password === values.passwordConfirmation, {
    path: ['passwordConfirmation'],
    message: 'As senhas não são iguais. Digite a mesma senha nos dois campos.',
  })

export type ConfirmResetValues = z.infer<typeof confirmResetSchema>

export const CONFIRM_RESET_FIELDS = ['password'] as const
