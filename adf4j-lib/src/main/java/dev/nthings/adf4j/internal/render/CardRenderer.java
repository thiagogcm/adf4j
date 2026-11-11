package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfCard;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Strings;
import org.jspecify.annotations.Nullable;

/// Renders smart-link nodes (block/inline/embed cards) to Markdown links.
final class CardRenderer {

  String renderBlockCard(AdfCard card, RenderContext context) {
    var session = context.session();
    var link = renderCardLink(card, context);
    if (link != null) {
      return link;
    }

    var identifier = Strings.firstNonBlank(card.datasourceId(), card.localId());
    if (identifier == null) {
      return session.writer().label("Card");
    }
    return session.writer().label("Card: " + identifier);
  }

  String renderInlineCard(AdfCard card, RenderContext context) {
    var link = renderCardLink(card, context);
    return link != null ? link : context.session().writer().label(AdfText.INLINE_CARD_LABEL);
  }

  String renderEmbedCard(AdfCard card, RenderContext context) {
    var link = renderCardLink(card, context);
    return link != null ? link : context.session().writer().label("Embed card");
  }

  /// Shared url/title rendering for all three card kinds, or `null` when the card has neither:
  /// url+title -> `[title](url)`; url only -> `<url>` (or `[url](url)` if not clean); title only ->
  /// the escaped title as plain text. A `PageLinkResolver` rewrites an internal page card's
  /// destination; a url-only card whose destination is rewritten keeps the original url as its
  /// visible label.
  private @Nullable String renderCardLink(AdfCard card, RenderContext context) {
    var session = context.session();
    var url = card.url();
    var title = card.title();
    var hasTitle = title != null && !title.isBlank();

    if (url != null && !url.isBlank()) {
      var resolvedUrl = session.resolvers().pageHref(url, card.attrs(), context.path());
      if (hasTitle) {
        return session.writer().link(title, resolvedUrl);
      }
      // url-only: an autolink needs an untouched url (not rewritten, not escaped); otherwise fall
      // back to a labelled link that keeps the original url visible.
      var rewritten = !resolvedUrl.equals(url);
      var destination = MarkdownText.escapeUrlDestination(resolvedUrl);
      return !rewritten && url.equals(destination) && isAbsoluteUri(url)
          ? "<%s>".formatted(url)
          : session.writer().link(url, resolvedUrl);
    }

    if (hasTitle) {
      return session.writer().text(title, false);
    }

    return null;
  }

  /// A CommonMark absolute-URI scheme: a letter, then letters/digits/+/-/., then ':' (min 2 chars).
  private static boolean isAbsoluteUri(String url) {
    var colon = url.indexOf(':');
    if (colon < 2 || !Character.isLetter(url.charAt(0))) {
      return false;
    }
    for (var i = 1; i < colon; i++) {
      var c = url.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '+' && c != '.' && c != '-') {
        return false;
      }
    }
    return true;
  }
}
