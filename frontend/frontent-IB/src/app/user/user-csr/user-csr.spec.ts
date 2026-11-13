import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserCsr } from './user-csr';

describe('UserCsr', () => {
  let component: UserCsr;
  let fixture: ComponentFixture<UserCsr>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserCsr]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserCsr);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
