export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  // Mostrar flujo de recetas médicas (subida en checkout, tab admin, avisos). En false se oculta sin borrar código.
  mostrarRecetas: false,
  // Google Maps Platform (Maps JS API, Places, Geocoding y Routes).
  // La key debe configurarse en tiempo de despliegue, nunca subida al repo.
googleMapsApiKey: '',
};
