import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/pages/app'; // Verifica que sea AppComponent
import { config } from './app/pages/app.config.server';

const bootstrap = () => bootstrapApplication(AppComponent, config);

export default bootstrap;