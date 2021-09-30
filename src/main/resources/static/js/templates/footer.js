let userId = $('#user-id').val()
let jwtToken = $('#jwt-token').val();
(function ($) {
    $(document).ready(function () {
        $('#subscribe-newsletter').SubscribeNewsletter();
        $('#shopping-cart-count').CartCount();
        $('#unread-message-count').UnreadMessageCount();
    });
})(jQuery);
