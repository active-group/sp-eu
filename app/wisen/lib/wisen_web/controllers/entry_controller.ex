defmodule WisenWeb.EntryController do
  alias Wisen.Domain, as: D
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

  def render(assigns) do
    ~H"""
    <h1>New resource</h1>

    <.form phx-change="change-title">
      <.input
        type="text"
        name="whah"
        value="fooo"
        phx-change="change-title"
      />
    </.form>

    <h2>Add properties</h2>
    <button phx-click="add_caters_to">caters to ... +</button>
    <button phx-click="add_placed_at">placed at ... +</button>

    """
  end

  def mount(_params, _session, socket) do
    {:ok, assign(socket, resource: D.Resource.make("New resource"))}
  end

  def handle_event("validate", m, socket) do
    {:noreply, assign(socket, m)}
  end
end
