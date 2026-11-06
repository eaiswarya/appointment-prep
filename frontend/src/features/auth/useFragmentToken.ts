import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'

/**
 * The token from an emailed link's fragment (`#token=…`), read once. Fragments never reach a
 * server or its logs (TECHNICAL_DESIGN §8.1). The fragment is then cleared from the address bar,
 * so the token isn't left in history or shared by copy-paste.
 */
export function useFragmentToken(): string | null {
  const location = useLocation()
  const navigate = useNavigate()
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token'))

  useEffect(() => {
    if (location.hash) {
      navigate(location.pathname, { replace: true })
    }
  }, [location.hash, location.pathname, navigate])

  return token
}
