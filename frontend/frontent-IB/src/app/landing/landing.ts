import { Component } from '@angular/core';
import {Router, RouterOutlet} from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  templateUrl: './landing.html',
  imports: [
    RouterOutlet
  ],
  styleUrls: ['./landing.css']
})
export class LandingComponent {
  title = 'CA App';
  constructor(private router: Router) {}

  async openLogin(e: Event) {
    e.preventDefault();
    await this.router.navigateByUrl('/login');
  }
}

