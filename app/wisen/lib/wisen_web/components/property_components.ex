defmodule WisenWeb.PropertyComponents do
  use Phoenix.Component

  def client_type(assigns) do
    ~H"""
    <div></div>
    """
  end

  def caters_to_property(assigns) do
    ~H"""
    <div>
      ... caters to
      <client_type selected={@which} />
    </div>
    """
  end
end
