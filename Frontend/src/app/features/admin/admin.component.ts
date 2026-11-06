import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Área restrita a ADMIN: a navegação entre as seções fica no menu lateral. */
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class AdminComponent {}
