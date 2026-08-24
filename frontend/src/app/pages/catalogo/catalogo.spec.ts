import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { CatalogoComponent } from './catalogo';

describe('Catalogo', () => {
  let component: CatalogoComponent;
  let fixture: ComponentFixture<CatalogoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogoComponent],
      providers: [provideHttpClient(), provideRouter([])]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CatalogoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
