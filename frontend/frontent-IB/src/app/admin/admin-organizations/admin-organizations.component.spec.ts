import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminOrganizationsComponent } from './admin-organizations.component';

describe('AdminOrganizationsComponent', () => {
  let component: AdminOrganizationsComponent;
  let fixture: ComponentFixture<AdminOrganizationsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminOrganizationsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminOrganizationsComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
