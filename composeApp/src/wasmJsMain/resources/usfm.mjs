export class JsMarker {
    constructor() {
        this.contents = [];
    }
    getIdentifier() { return ""; }
    getPosition() { return this.position; }
    setPosition(value) { this.position = value; }
    getAllowedContents() { return []; }
    preProcess(input) { return ""; }
    tryInsert(input) {
        if (this.contents.length > 0 && this.contents[this.contents.length - 1].tryInsert(input)) {
            return true;
        } else if (this.getAllowedContents().includes(input.constructor)) {
            this.contents.push(input);
            return true;
        } else {
            return false;
        }
    }
    getTypesPathToLastMarker() {}
    getHierarchyToMarker(target) {}
    getHierarchyToMultipleMarkers(targets) {}
    getChildMarkers(clazz) {
        return this.getChildMarkersIgnored(clazz, []);
    }
    getChildMarkersIgnored(clazz, ignoredParents) {
        const outMarkers = [];
        const stack = [];

        if (ignoredParents.includes(this.constructor)) {
            return [];
        } else {
            stack.push(this);
            while (stack.length > 0) {
                const marker = stack.pop();

                if (marker instanceof clazz) {
                    outMarkers.push(marker);
                }

                marker.contents.forEach((m) => {
                    if (!ignoredParents.includes(m.constructor)) {
                        stack.push(m);
                    }
                });
            }
        }

        return outMarkers.reverse()
    }
    getLastDescendant() {}
}
export class JsUsfmDocument extends JsMarker {
    constructor() {
        super();
        this.contents = [];
    }
    getIdentifier() { return ""; }
    getAllowedContents() {
        return [JsTOC3Marker, JsHMarker, JsCMarker];
    }
    insert(input) {
        if (!this.tryInsert(input)) {
            this.contents.push(input);
        }
    }
}
export class JsTOC3Marker extends JsMarker {
    constructor(bookAbbreviation) {
        super();
        this.preProcess(bookAbbreviation);
    }
    getIdentifier() { return "toc3"; }
    preProcess(input) {
        this.bookAbbreviation = input.trim();
        return "";
    }
}
export class JsHMarker extends JsMarker {
    constructor(headerText) {
        super();
        this.preProcess(headerText);
    }
    getIdentifier() { return "h"; }
    preProcess(input) {
        this.headerText = input.trim();
        return "";
    }
}
export class JsCMarker extends JsMarker {
    constructor(number) {
        super();
        this.number = this.preProcess(number);
    }
    getIdentifier() { return "c"; }
    preProcess(input) { return input; }
    getAllowedContents() {
        return [JsVMarker, JsTextBlock, JsFMarker];
    }
}
export class JsVMarker extends JsMarker {
    constructor(number) {
        super();
        this.number = this.preProcess(number);
        this.startingVerse = this.number;
        this.endingVerse = this.number;
    }
    getIdentifier() { return "v"; }
    tryInsert(input) {
        if (input instanceof JsVMarker) {
            return false;
        } else {
            return super.tryInsert(input);
        }
    }
    preProcess(input) {
        return input;
    }
    getAllowedContents() {
        return [JsFMarker, JsTextBlock, JsXMarker];
    }
}
export class JsFMarker extends JsMarker {
    constructor(footNoteCaller) {
        super();
        this.preProcess(footNoteCaller);
    }
    getIdentifier() { return "f"; }
    preProcess(input) {
        this.footNoteCaller = input.trim();
        return "";
    }
}
export class JsXMarker extends JsMarker {
    constructor(crossRefCaller) {
        super();
        this.preProcess(crossRefCaller);
    }
    getIdentifier() { return "x"; }
    preProcess(input) {
        this.crossRefCaller = input.trim();
        return "";
    }
}
export class JsTextBlock extends JsMarker {
    constructor(text) {
        super();
        this.text = text;
    }
    getIdentifier() { return ""; }
}

export const convertBook = (obj) => {
    const document = new JsUsfmDocument();

    obj.headers.forEach((header) => {
        switch (header.tag) {
            case "h":
                document.insert(new JsHMarker(header.content));
                break;
            case "toc3":
                document.insert(new JsTOC3Marker(header.content));
                break;
            default:
                break;
        }
    });

    for (const [c, cObj] of Object.entries(obj.chapters)) {
        document.insert(new JsCMarker(c));

        for (const [v, vObj] of Object.entries(cObj)) {
            document.insert(new JsVMarker(v));

            for (const [vo, voObj] of Object.entries(vObj.verseObjects)) {
                if (voObj.text !== undefined) {
                    document.insert(new JsTextBlock(voObj.text));
                }
            }
        }
    }

    return document;
}