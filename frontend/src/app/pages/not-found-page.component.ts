import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, MatButtonModule],
  template: `
    <section class="not-found">
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist or was deleted.</p>
      <a mat-flat-button routerLink="/projects">Go to my projects</a>
    </section>
  `,
  styles: `.not-found { max-width: 480px; margin: 80px auto; text-align: center; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPageComponent {}
