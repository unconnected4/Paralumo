// API models, generated from docs/api/openapi.json into ./schema.d.ts by `npm run gen:api`.
// Only names are given here; edit the backend DTOs and regenerate to change a shape.
import type { components } from './schema';

type Schemas = components['schemas'];

export type ExampleSentence = Schemas['ExampleSentence'];
export type Sense = Schemas['Sense'];
export type Lexeme = Schemas['Lexeme'];
export type LookupResponse = Schemas['LookupResponse'];
export type Card = Schemas['Card'];
export type User = Schemas['User'];
