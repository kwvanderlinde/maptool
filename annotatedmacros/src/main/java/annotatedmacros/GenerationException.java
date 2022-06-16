package annotatedmacros;

import javax.lang.model.element.Element;

public class GenerationException extends RuntimeException {
    private final Element context;

    public GenerationException(String message, Element context) {
        super(message);
        this.context = context;
    }

    public Element getContext() {
        return this.context;
    }
}
