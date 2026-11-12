package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfCard;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.UrlSafety;
import org.jspecify.annotations.Nullable;

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

  /// Shared by all card kinds; `null` when the card has neither url nor title. url+title ->
  /// `[title](url)`; url only -> `<url>` (or `[url](url)` if not clean); title only -> plain text.
  /// A rewritten page destination keeps the original url as the visible label.
  private @Nullable String renderCardLink(AdfCard card, RenderContext context) {
    var session = context.session();
    var url = card.url();
    var title = card.title();
    var hasTitle = title != null && !title.isBlank();

    if (url != null && !url.isBlank()) {
      var resolvedUrl = session.resources().pageHref(url, card.attrs(), context.path());
      if (hasTitle) {
        return session.writer().link(title, resolvedUrl);
      }
      // Autolink only an untouched url; otherwise a labelled link keeps the original visible.
      var rewritten = !resolvedUrl.equals(url);
      var destination = MarkdownText.escapeUrlDestination(resolvedUrl);
      return !rewritten && url.equals(destination) && UrlSafety.isAbsoluteUri(url)
          ? "<%s>".formatted(url)
          : session.writer().link(url, resolvedUrl);
    }

    if (hasTitle) {
      return session.writer().text(title, false);
    }

    return null;
  }
}
