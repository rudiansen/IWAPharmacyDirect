(function ($) {
    $(document).ready(function () {
        let cart = $('#cart').val();
        $('#order-summary').OrderSummary({ cart: cart, currencySymbol: /*[[${currencySymbol}]]*/ '&#164;' });
    });
})(jQuery);
