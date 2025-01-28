defmodule WisenWeb.EntryController do
  alias Wisen.Domain, as: D
  alias WisenWeb.Items, as: I
  use WisenWeb, :live_view

  # An entry consists of
  # * A title (string)
  # * A list of properties

  # A property is one of
  # * caters_to
  # * placed_at
  # * addresses_need
  # * hosts_event

  # caters_to is one of
  # * elderly
  # * queer
  # * immigrant

  def title_field_name do
    "furz"
  end

  def some_item do
    I.inp("Some input")
  end

  def property(assigns) do
    case assigns.property do
      %D.CatersToProperty{which: which} ->
        ~H"""
          ... caters to
        <.form phx-change="change-caters-to-which" phx-value-index={@index}>
          <.input
            type="select"
            name={"which"}
            label="Caters to whom tho?"
            options={[:elderly, :queer, :immigrant]}
            value={which}
          />
        </.form>
        """
        _ -> "Unknown"
    end
  end

  def render(assigns) do
    ~H"""
    <h1>New resource</h1>

    <% IO.inspect(assigns) %>

    <.form phx-change="change-title">
      <.input
        type="text"
        name={title_field_name()}
        value="fooo"
      />
    </.form>

    <h2>Properties</h2>

    <ul>
    <%= for {prop, index} <- Enum.with_index(@resource.properties) do %>
      <li class="border p-2">
        <.property index={index} property={prop} />
      </li>
    <% end %>
    </ul>

    <h2>Add properties</h2>
    <button phx-click="add_caters_to">caters to ... +</button>



    """
  end

  def mount(_params, _session, socket) do
    {:ok, assign(socket, resource: D.Resource.make("New resource", []))}
  end

  def handle_event("change-title", session, socket) do
    {:noreply, update(socket, :resource,
      &(D.set_title(&1, session[title_field_name()])))}
  end

  def handle_event("add_caters_to", session, socket) do
    {:noreply, update(socket, :resource, &D.add_empty_caters_to/1)}
  end

  def handle_event("change-caters-to-which",
                   %{"index" => index, "which" => new_which},
                   socket) do
    index = String.to_integer(index)
    new_which = String.to_atom(new_which)
    {:noreply, update(socket, :resource,
                      fn resource ->
                        %D.Resource{resource |
                          properties:
                            List.update_at(resource.properties, index, fn old_property ->
                              %D.CatersToProperty{old_property | which: new_which}
                            end)}
                      end)}
  end

end
