defmodule WisenWeb.RoomChannel do
  use Phoenix.Channel

  def join("room:lobby", _message, socket) do
    {:ok, socket}
  end

  def join("room:" <> _private_room_id, _params, _socket) do
    {:error, %{reason: "unauthorized"}}
  end

  def handle_in("new-msg", %{"body" => body}, socket) do
    push(socket, "new-state", %{body: "und ja moin"})

    {:noreply, socket}
  end

  def handle_in("search", %{"query" => query}, socket) do
    push(socket, "new-state", %{body: [%{"subject" => "jürgen", "predicate" => "ist", "object" => "cool"}]})

    {:noreply, socket}
  end
end
