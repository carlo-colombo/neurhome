# Application Categories

This is the delivery index for application categories in Neurhome. Each story
is a thin vertical slice and includes its own automated tests.

## Shared Rules

- Home remains the start page.
- A left swipe moves into the category pager, with the next page entering from
  the right.
- Uncategorized contains applications with no tags.
- Uncategorized is always the last category page. With no user tags, it is the
  only category page.
- Once reordering is implemented, user tag screens are ordered by their
  persisted screen order, not by name.
- Applications on category screens use the existing `ApplicationsList`.
- Applications within each screen are sorted alphabetically by label,
  case-insensitively.
- An application can have zero, one, or more tags.
- Application identity is package name plus Android user/profile.

## Story Files

1. [Story 1: Enter Uncategorized by Swiping Left](application-categories/story-01-uncategorized-swipe.md)
2. [Story 2: Create a Tag and Screen](application-categories/story-02-create-tag.md)
3. [Story 3: Assign Tags from Application Detail](application-categories/story-03-assign-tags.md)
4. [Story 4: Show Tagged Applications](application-categories/story-04-show-tagged-applications.md)
5. [Story 5: Remove a Tag from Application Detail](application-categories/story-05-remove-tag-from-application.md)
6. [Story 6: Remove a Tag Screen](application-categories/story-06-remove-tag-screen.md)
7. [Story 7: Reorder Tag Screens](application-categories/story-07-reorder-tag-screens.md)

## Delivery Order

Deliver Stories 1 through 3 first for navigation, durable tag creation, and
assignment. Stories 4 through 6 complete categorization and cleanup. Story 7
then makes the category pager user-configurable while preserving Uncategorized
as the final screen.
