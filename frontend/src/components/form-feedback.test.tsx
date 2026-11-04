import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm } from 'react-hook-form'
import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { applyApiFieldErrors } from '../lib/form-errors'
import { HttpError } from '../lib/http'
import { FormAlert } from './FormAlert'
import { TextField } from './TextField'

type Valores = { amount: string; description: string }
const CAMPOS = ['amount', 'description'] as const

/** Small form that receives a backend error, like a real screen would. */
function FormularioComErroDoServidor({ erro }: { erro: unknown }) {
  const {
    register,
    setError,
    formState: { errors },
  } = useForm<Valores>()
  const aplicou = () => applyApiFieldErrors(erro, CAMPOS, setError)

  return (
    <form>
      <TextField
        label="Valor"
        error={errors.amount?.message}
        {...register('amount')}
      />
      <TextField
        label="Descrição"
        error={errors.description?.message}
        {...register('description')}
      />
      <button
        type="button"
        onClick={() => {
          document.body.dataset.aplicou = String(aplicou())
        }}
      >
        Enviar
      </button>
    </form>
  )
}

describe('TextField', () => {
  it('liga o rótulo ao campo e mostra a dica', () => {
    render(<TextField label="Valor" hint="Use vírgula para os centavos." />)

    const campo = screen.getByLabelText('Valor')
    expect(campo).toHaveAttribute('aria-invalid', 'false')
    expect(campo).toHaveAccessibleDescription('Use vírgula para os centavos.')
  })

  it('destaca o campo e descreve o erro para leitores de tela (ONF02)', () => {
    render(
      <TextField
        label="Valor"
        hint="Use vírgula para os centavos."
        error="O valor deve ser maior que zero."
      />,
    )

    const campo = screen.getByLabelText('Valor')
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(campo).toHaveAccessibleDescription(
      'O valor deve ser maior que zero. Use vírgula para os centavos.',
    )
  })
})

describe('FormAlert', () => {
  it('leva o foco para o erro geral do formulário', () => {
    render(<FormAlert tone="error">Não foi possível salvar.</FormAlert>)

    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('anuncia o sucesso sem tirar o foco do usuário', () => {
    render(<FormAlert tone="success">Salvo com sucesso.</FormAlert>)

    expect(screen.getByRole('status')).not.toHaveFocus()
  })
})

describe('applyApiFieldErrors', () => {
  it('coloca os erros do servidor nos campos certos e foca o primeiro', async () => {
    const user = userEvent.setup()
    const erro = new HttpError(400, {
      code: 'VALIDATION_ERROR',
      message: 'Os dados informados são inválidos.',
      fieldErrors: {
        amount: 'O valor deve ser maior que zero.',
        description: 'A descrição deve ter no máximo 120 caracteres.',
        categoryId: 'Campo que não existe nesta tela.',
      },
    })
    render(<FormularioComErroDoServidor erro={erro} />)

    await user.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(screen.getByLabelText('Valor')).toHaveAccessibleDescription(
      'O valor deve ser maior que zero.',
    )
    expect(screen.getByLabelText('Descrição')).toHaveAccessibleDescription(
      'A descrição deve ter no máximo 120 caracteres.',
    )
    expect(screen.getByLabelText('Valor')).toHaveFocus()
    expect(
      screen.queryByText('Campo que não existe nesta tela.'),
    ).not.toBeInTheDocument()
    expect(document.body.dataset.aplicou).toBe('true')
  })

  it('avisa quando nenhum erro é de campo, para a tela mostrar um erro geral', async () => {
    const user = userEvent.setup()
    render(<FormularioComErroDoServidor erro={new HttpError(500, null)} />)

    await user.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(document.body.dataset.aplicou).toBe('false')
    expect(screen.getByLabelText('Valor')).toHaveAttribute(
      'aria-invalid',
      'false',
    )
  })
})

describe('mensagens padrão de validação (ONF02)', () => {
  const mensagens = (schema: z.ZodType, valor: unknown) =>
    schema.safeParse(valor).error?.issues.map((issue) => issue.message)

  it('usam português simples, sem termos técnicos', () => {
    expect(mensagens(z.string(), undefined)).toEqual(['Preencha este campo.'])
    expect(mensagens(z.string().min(1), '')).toEqual(['Preencha este campo.'])
    expect(mensagens(z.string().min(3), 'ab')).toEqual([
      'Use pelo menos 3 caracteres.',
    ])
    expect(mensagens(z.string().max(120), 'a'.repeat(121))).toEqual([
      'Use no máximo 120 caracteres.',
    ])
    expect(mensagens(z.number(), 'dez')).toEqual(['Informe um número.'])
    expect(mensagens(z.number().positive(), 0)).toEqual([
      'Informe um valor maior que 0.',
    ])
    expect(mensagens(z.email(), 'maria')).toEqual(['Informe um e-mail válido.'])
  })

  it('respeitam a mensagem própria do schema quando existe', () => {
    expect(mensagens(z.string().min(1, 'Informe a descrição.'), '')).toEqual([
      'Informe a descrição.',
    ])
  })
})
