import { z } from 'zod'

/** `todayIso` (YYYY-MM-DD) is the latest payment date allowed. */
export function createDasPaymentSchema(todayIso: string) {
  return z.object({
    paidAt: z
      .string()
      .min(1, 'Informe a data do pagamento.')
      .refine(
        (date) => date <= todayIso,
        'A data do pagamento não pode ser futura.',
      ),
  })
}

export type DasPaymentFormValues = z.infer<
  ReturnType<typeof createDasPaymentSchema>
>

export const DAS_PAYMENT_FIELDS = ['paidAt'] as const
