import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CaHome } from './ca-home';

describe('CaHome', () => {
  let component: CaHome;
  let fixture: ComponentFixture<CaHome>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CaHome]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CaHome);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
