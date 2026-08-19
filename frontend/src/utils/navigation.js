function registeredNavigationRoutes(router, store) {
  const layoutRoute = router.options.routes.find(route => route.path === '/')
  return ((layoutRoute && layoutRoute.children) || [])
    .filter(route => route.meta && route.meta.nav)
    .filter(route => store.getters['session/hasPermission'](route.meta.permission))
    .map(route => ({ ...route, path: `/${route.path}` }))
}

/** Resolve the first usable server-driven menu while keeping registered routes as a whitelist. */
export function resolveHomePath(router, store) {
  const registered = registeredNavigationRoutes(router, store)
  const registeredPaths = new Set(registered.map(route => route.path))
  const navigation = store.state.navigation

  if (navigation.status === 'ready') {
    const first = navigation.items.find(item => registeredPaths.has(item.routePath))
    return first ? first.routePath : '/forbidden'
  }

  // Navigation is an authorization response. If it cannot be verified, fail closed.
  if (navigation.status === 'error') return '/forbidden'
  return '/forbidden'
}

export function isUsableNavigationPath(router, store, target) {
  if (typeof target !== 'string' || !target.startsWith('/')) return false
  const resolvedPath = router.resolve(target).route.path
  const registeredPaths = new Set(registeredNavigationRoutes(router, store).map(route => route.path))
  if (!registeredPaths.has(resolvedPath)) return false
  if (store.state.navigation.status === 'ready') {
    return store.state.navigation.items.some(item => item.routePath === resolvedPath)
  }
  return false
}
