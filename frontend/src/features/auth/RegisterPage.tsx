import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'
import { FormField } from '../../components/FormField'
import { useRegister } from './useRegister'

// Quick client-side checks; the server applies the full rules (e.g. common passwords).
const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(120),
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters'),
})

type FormValues = z.infer<typeof schema>
type FormFieldName = keyof FormValues

const FORM_FIELDS: readonly string[] = ['fullName', 'email', 'password'] satisfies FormFieldName[]

// Server fields with no input on this form; their errors go in the page alert instead.
const OTHER_FIELD_LABELS: Record<string, string> = { timezone: 'Time zone' }

function capitalize(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function RegisterPage() {
  const navigate = useNavigate()
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  const signUp = useRegister()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  function showProblem(problem: ApiProblem) {
    if (problem.code === 'EMAIL_TAKEN') {
      setError('email', { message: errorMessage('EMAIL_TAKEN') }, { shouldFocus: true })
      return
    }
    const fieldErrors = problem.errors ?? []
    const onForm = fieldErrors.filter((e) => FORM_FIELDS.includes(e.field))
    const elsewhere = fieldErrors.filter((e) => !FORM_FIELDS.includes(e.field))

    onForm.forEach((e, i) =>
      setError(e.field as FormFieldName, { message: capitalize(e.message) }, { shouldFocus: i === 0 }),
    )
    if (elsewhere.length > 0) {
      setError('root', {
        message: elsewhere.map((e) => `${OTHER_FIELD_LABELS[e.field] ?? e.field} ${e.message}.`).join(' '),
      })
    } else if (onForm.length === 0) {
      setError('root', { message: errorMessage(problem.code) })
    }
  }

  function onSubmit(values: FormValues) {
    signUp.mutate(
      { ...values, timezone },
      {
        onSuccess: (user) => navigate('/login', { state: { registered: true, email: user.email } }),
        onError: (error) => showProblem(error instanceof ApiError ? error.problem : {}),
      },
    )
  }

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Create your account</h1>

      <form noValidate className="mt-6 space-y-4" onSubmit={handleSubmit(onSubmit)}>
        {errors.root && (
          <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errors.root.message}
          </p>
        )}

        <FormField
          id="fullName"
          label="Full name"
          autoComplete="name"
          error={errors.fullName?.message}
          {...register('fullName')}
        />
        <FormField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters. Avoid common passwords."
          error={errors.password?.message}
          {...register('password')}
        />

        <p className="text-sm text-gray-600">
          Your time zone: <span className="font-medium">{timezone}</span>. You can change it later in
          your profile.
        </p>

        <button
          type="submit"
          disabled={signUp.isPending}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {signUp.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-sm text-gray-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-blue-700 underline">
          Log in
        </Link>
      </p>
    </main>
  )
}
