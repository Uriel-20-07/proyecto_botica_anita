export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  // Mostrar flujo de recetas médicas (subida en checkout, tab admin, avisos). En false se oculta sin borrar código.
  mostrarRecetas: false,
  geoapifyApiKey: '5877badf92e14ccb85d2a16ede4aa1dc',
  // Google Maps Platform (Maps JS API, Places, Geocoding y Routes).
  // Recomendado: restringir esta clave por referrer HTTP en Google Cloud Console.
  googleMapsApiKey: 'AIzaSyDyLofI1wMc3uwQdvEWPU3160X0DeC8asQ',
};
