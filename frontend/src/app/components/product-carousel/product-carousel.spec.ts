import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { ProductCarouselComponent } from './product-carousel';

describe('ProductCarouselComponent', () => {
  let component: ProductCarouselComponent;
  let fixture: ComponentFixture<ProductCarouselComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductCarouselComponent],
      providers: [provideHttpClient()]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProductCarouselComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
