import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';

import { NavbarComponent } from './navbar';
import { AuthService } from '../../services/auth.service';
import { AuthModalService } from '../../services/auth-modal.service';

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let authService: AuthService;
  let authModalService: AuthModalService;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [provideHttpClient(), provideRouter([])]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService);
    authModalService = TestBed.inject(AuthModalService);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to /cargar-receta if user is authenticated on handleCargarReceta', () => {
    spyOn(authService, 'isAuthenticated').and.returnValue(true);
    const navigateSpy = spyOn(router, 'navigate');
    const dummyEvent = new Event('click');

    component.handleCargarReceta(dummyEvent);

    expect(navigateSpy).toHaveBeenCalledWith(['/cargar-receta']);
  });

  it('should open login modal if user is not authenticated on handleCargarReceta', () => {
    spyOn(authService, 'isAuthenticated').and.returnValue(false);
    const openSpy = spyOn(authModalService, 'open');
    const dummyEvent = new Event('click');

    component.handleCargarReceta(dummyEvent);

    expect(openSpy).toHaveBeenCalledWith('login');
  });
});
