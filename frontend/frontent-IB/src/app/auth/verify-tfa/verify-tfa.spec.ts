import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VerifyTfa } from './verify-tfa';

describe('VerifyTfa', () => {
  let component: VerifyTfa;
  let fixture: ComponentFixture<VerifyTfa>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyTfa]
    })
    .compileComponents();

    fixture = TestBed.createComponent(VerifyTfa);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
