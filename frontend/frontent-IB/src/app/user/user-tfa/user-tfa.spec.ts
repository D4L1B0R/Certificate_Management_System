import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserTfa } from './user-tfa';

describe('UserTfa', () => {
  let component: UserTfa;
  let fixture: ComponentFixture<UserTfa>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserTfa]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserTfa);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
