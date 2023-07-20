# Concept
    
We have some things in our data model which depend on the current state of the client. Especially we depend on which zone is current in order to do our stuff, and sometimes even require access to the zone _renderer_ that is current. In addition to just being a poor separation of concerns, it also means we can't do interesting things with our model such as run it headlessly without any renderers.

In general, we should consider not only a separation of concerns (i.e., which components are responsible for what) but also a separation of units / operations (i.e., which functionality should be co-located). As an example of both, consider `Zone.isTokenVisible()`. The lack separation of concerns can be seen by the method needing to reach out to the frame in order to get the current `PlayerView`. The lack of separation of units can be seen by asking whether the token is owned by the current player. We could easily separate that out as a completely separate concept from regular visibility.

# Details

## `Zone.isTokenVisible(Token)`

One option is to keep this method inside `Zone` and then rework it to `Zone.isTokenVisible(Token, PlayerView)` while also separating out the only-visible-to-owner logic. Note that some callers actually alraedy have a `PlayerView` but it is thrown away, e.g., `AppUtil.isTokenVisible(Zone, Token, PlayerView)`.

The other option is to move this question somewhere else. This is tempting because we aren't really asking the model whether the token is visible, but rather whether the view can be expected to show the token (save for bounds).

## flush() methods

Both `ZoneRenderer.flush()` and `ZoneView.flush()` are currently required because they do not properly encapsulate their state. That is, potentially any part of the program that modifies any state *must* be aware of how that state could affect rendering. They must then flush cached state from `ZoneRenderer` and `ZoneView` so that rendering updates according to state changes. However it is also important that only necessary state is flushed, otherwise we can have performance degredation.

This situation is untenable, increasing the coupling between components, asking for stale state during rendering, and confusing readers of the code as there is more for them to keep track of. What we should instead do is fire events in the model as it changes. Then `ZoneView` can subscribe to any important events and modify its internal state accordingly. `ZoneRenderer` can do so as well, but in principle I would like only `ZoneView` to need to do this kind of thing and can also control when the `ZoneRenderer` renders again. In the future I hope `ZoneRenderer` maintains zero important state, with only `ZoneView` (or a related view model class) containing the state.
