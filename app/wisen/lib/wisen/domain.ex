require QuickStruct

defmodule Wisen.Domain do

  # Properties

  # which is on of :elderly, :queer, :immigrant
  QuickStruct.define_module(CatersToProperty, [:which])

  QuickStruct.define_module(PlacedAtProperty, [:longitude, :latitude])

  # which is one of :social, :education, :culture, :food, ...
  QuickStruct.define_module(AddressesNeedProperty, [:which])

  # QuickStruct.define_module(Event, [:title, :when, :where])

  QuickStruct.define_module(HostsEventProperty, [event: Event.t])

  QuickStruct.define_module(HasGoogleMapsProperty, [uri: String.t])

  QuickStruct.define_module(HasWebsiteProperty, [uri: String.t])

  QuickStruct.define_module(Titled, [title: String.t])

  # Resource

  QuickStruct.define_module(Resource, [:properties])

end
